package com.foacraft.mcp.triton.triton

import com.rexcantor64.triton.api.TritonAPI
import org.slf4j.Logger
import java.lang.reflect.Method
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * All Triton internal API access is done here via reflection,
 * because Velocity isolates plugin classloaders and our classloader
 * cannot see Triton's internal classes directly.
 */
class TritonBridge(
    private val logger: Logger
) {
    private var storageObj: Any? = null
    private var tritonClassLoader: ClassLoader? = null
    private val lock = ReentrantReadWriteLock()

    // Cached reflected classes (loaded from Triton's classloader)
    private lateinit var langTextClass: Class<*>
    private lateinit var tritonCollectionClass: Class<*>

    fun initialize() {
        val api = TritonAPI.getInstance()
            ?: throw IllegalStateException("Triton API returned null — is Triton installed?")

        tritonClassLoader = api.javaClass.classLoader

        storageObj = api.javaClass.findMethodInHierarchy("getStorage")
            ?.also { it.isAccessible = true }
            ?.invoke(api)
            ?: throw IllegalStateException("getStorage() returned null — Triton may not have finished loading.")

        val cl = tritonClassLoader!!
        langTextClass = cl.loadClass("com.rexcantor64.triton.language.item.LanguageText")
        tritonCollectionClass = cl.loadClass("com.rexcantor64.triton.language.item.Collection")

        logger.info("TritonBridge initialized successfully.")
    }

    // ── Data models ───────────────────────────────────────────────────────────

    data class LanguageInfo(val name: String, val displayName: String, val isMain: Boolean)

    data class CollectionInfo(
        val name: String,
        val itemCount: Int,
        val blacklist: Boolean,
        val servers: List<String>
    )

    /**
     * Represents a single translation entry.
     * @param languages map of language name → translated text
     * @param patterns  text patterns used for automatic in-chat replacement
     * @param servers   server filter list; null means inherit from collection metadata
     * @param blacklist true = exclude listed servers, false = whitelist; null = inherit from collection
     */
    data class ItemSummary(
        val key: String,
        val collection: String,
        val type: String,
        val languages: Map<String, String>,
        val patterns: List<String>,
        val servers: List<String>?,
        val blacklist: Boolean?
    )

    // ── Read operations ───────────────────────────────────────────────────────

    fun getLanguages(): List<LanguageInfo> {
        val manager = TritonAPI.getInstance().languageManager
        val mainLang = manager.mainLanguage.name
        return manager.allLanguages.map { lang ->
            LanguageInfo(lang.name, lang.rawDisplayName, lang.name == mainLang)
        }
    }

    fun listCollections(): List<CollectionInfo> = lock.read {
        getCollectionsMap().map { (name, col) ->
            val meta = col.invokeR("getMetadata")
            CollectionInfo(
                name = name,
                itemCount = (col.invokeR("getItems") as? List<*>)?.size ?: 0,
                blacklist = meta?.invokeR("isBlacklist") as? Boolean ?: true,
                servers = (meta?.invokeR("getServers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }
    }

    fun getCollectionItems(collectionName: String, typeFilter: String? = null): List<ItemSummary> =
        lock.read {
            val col = getCollectionsMap()[collectionName] ?: return emptyList()
            val items = (col.invokeR("getItems") as? List<*>) ?: return emptyList()
            items.filterNotNull()
                .filter { item ->
                    val typeName = item.invokeR("getType")?.invokeR("getName") as? String ?: ""
                    typeFilter == null || typeName.equals(typeFilter, ignoreCase = true)
                }
                .map { item -> toSummary(item, collectionName) }
        }

    fun findItemByKey(key: String): ItemSummary? = lock.read {
        for ((colName, col) in getCollectionsMap()) {
            val items = (col.invokeR("getItems") as? List<*>) ?: continue
            val item = items.filterNotNull().find { it.invokeR("getKey") == key }
            if (item != null) return toSummary(item, colName)
        }
        null
    }

    fun searchItems(
        keyPattern: String? = null,
        contentQuery: String? = null,
        missingLanguage: String? = null,
        collection: String? = null,
        limit: Int = 50
    ): List<ItemSummary> = lock.read {
        val keyRegex = keyPattern?.let { Regex(it, RegexOption.IGNORE_CASE) }
        val results = mutableListOf<ItemSummary>()
        val scope = if (collection != null) getCollectionsMap().filter { it.key == collection } else getCollectionsMap()

        outer@ for ((colName, col) in scope) {
            val items = (col.invokeR("getItems") as? List<*>) ?: continue
            for (item in items.filterNotNull()) {
                if (results.size >= limit) break@outer
                val itemKey = item.invokeR("getKey") as? String ?: continue
                @Suppress("UNCHECKED_CAST")
                val langs = (item.invokeR("getLanguages") as? Map<String, String>) ?: emptyMap()

                if (keyRegex != null && !keyRegex.containsMatchIn(itemKey)) continue
                if (contentQuery != null && langs.values.none { it.contains(contentQuery, ignoreCase = true) }) continue
                if (missingLanguage != null && langs.containsKey(missingLanguage)) continue
                results.add(toSummary(item, colName))
            }
        }
        results
    }

    // ── Write operations ──────────────────────────────────────────────────────

    data class UpsertResult(val created: Int, val updated: Int, val errors: List<String>)

    fun upsertTextItem(
        collectionName: String,
        key: String,
        translations: Map<String, String>,
        servers: List<String>? = null,
        blacklist: Boolean? = null
    ): Boolean = lock.write {
        val collections = getCollectionsMap()
        val col = collections.getOrPut(collectionName) { newCollectionInstance() }
        val items = col.invokeR("getItems") as? MutableList<Any?> ?: return false
        val existing = items.filterNotNull().find { it.invokeR("getKey") == key }

        if (existing != null) {
            @Suppress("UNCHECKED_CAST")
            val merged = HashMap((existing.invokeR("getLanguages") as? Map<String, String>) ?: emptyMap())
            merged.putAll(translations)
            existing.invokeRSet("setLanguages", merged)
            servers?.let { existing.invokeRSet("setServers", it) }
            blacklist?.let { existing.invokeRSet("setBlacklist", it) }
        } else {
            items.add(newTextItemInstance(key, translations, servers, blacklist))
        }

        val changedItem = items.filterNotNull().find { it.invokeR("getKey") == key }!!
        callUploadPartially(collections, listOf(changedItem), emptyList())
        true
    }

    fun batchUpsertTextItems(
        collectionName: String,
        items: List<Map<String, Any?>>
    ): UpsertResult = lock.write {
        val collections = getCollectionsMap()
        val col = collections.getOrPut(collectionName) { newCollectionInstance() }
        val itemList = col.invokeR("getItems") as? MutableList<Any?>
            ?: return UpsertResult(0, 0, listOf("Could not access collection items"))
        var created = 0
        var updated = 0
        val errors = mutableListOf<String>()
        val changedItems = mutableListOf<Any>()

        for (itemData in items) {
            try {
                @Suppress("UNCHECKED_CAST")
                val key = itemData["key"] as? String ?: throw IllegalArgumentException("Missing 'key'")
                @Suppress("UNCHECKED_CAST")
                val translations = itemData["translations"] as? Map<String, String>
                    ?: throw IllegalArgumentException("Missing 'translations'")

                val existing = itemList.filterNotNull().find { it.invokeR("getKey") == key }
                if (existing != null) {
                    @Suppress("UNCHECKED_CAST")
                    val merged = HashMap((existing.invokeR("getLanguages") as? Map<String, String>) ?: emptyMap())
                    merged.putAll(translations)
                    existing.invokeRSet("setLanguages", merged)
                    changedItems.add(existing)
                    updated++
                } else {
                    val newItem = newTextItemInstance(key, translations)
                    itemList.add(newItem)
                    changedItems.add(newItem)
                    created++
                }
            } catch (e: Exception) {
                errors.add("Item '${itemData["key"]}': ${e.message}")
            }
        }

        if (changedItems.isNotEmpty()) callUploadPartially(collections, changedItems, emptyList())
        UpsertResult(created, updated, errors)
    }

    fun deleteItem(key: String): Boolean = lock.write {
        val collections = getCollectionsMap()
        for ((_, col) in collections) {
            val items = col.invokeR("getItems") as? MutableList<Any?> ?: continue
            val item = items.filterNotNull().find { it.invokeR("getKey") == key }
            if (item != null) {
                items.remove(item)
                callUploadPartially(collections, emptyList(), listOf(item))
                return true
            }
        }
        false
    }

    fun reloadTriton() {
        try {
            TritonAPI.getInstance().reload()
            logger.info("Triton reloaded via MCP request.")
        } catch (e: Exception) {
            logger.error("Error during Triton reload: {}", e.message)
            throw e
        }
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun getCollectionsMap(): MutableMap<String, Any> {
        val storage = storageObj ?: error("TritonBridge not initialized")
        return (storage.javaClass.findMethodInHierarchy("getCollections")
            ?.also { it.isAccessible = true }
            ?.invoke(storage) as? MutableMap<String, Any>)
            ?: mutableMapOf()
    }

    private fun callUploadPartially(collections: MutableMap<String, Any>, changed: List<Any>, deleted: List<Any>) {
        val storage = storageObj ?: return
        val method = storage.javaClass.findMethodInHierarchy("uploadPartiallyToStorage")
        if (method != null) {
            method.isAccessible = true
            method.invoke(storage, collections, changed, deleted)
        } else {
            storage.javaClass.findMethodInHierarchy("uploadToStorage")
                ?.also { it.isAccessible = true }
                ?.invoke(storage, collections)
        }
    }

    private fun newCollectionInstance(): Any =
        tritonCollectionClass.getDeclaredConstructor().newInstance()

    private fun newTextItemInstance(
        key: String,
        translations: Map<String, String>,
        servers: List<String>? = null,
        blacklist: Boolean? = null
    ): Any {
        val item = langTextClass.getDeclaredConstructor().newInstance()
        item.invokeRSet("setKey", key)
        item.invokeRSet("setLanguages", HashMap(translations))
        servers?.let { item.invokeRSet("setServers", it) }
        blacklist?.let { item.invokeRSet("setBlacklist", it) }
        return item
    }

    private fun toSummary(item: Any, collectionName: String): ItemSummary {
        @Suppress("UNCHECKED_CAST")
        val langs = (item.invokeR("getLanguages") as? Map<String, String>) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val patterns = (item.invokeR("getPatterns") as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val servers = (item.invokeR("getServers") as? List<String>)
        val blacklist = item.invokeR("getBlacklist") as? Boolean
        val typeName = (item.invokeR("getType")?.invokeR("getName") as? String ?: "text").lowercase()
        return ItemSummary(
            key = item.invokeR("getKey") as? String ?: "",
            collection = collectionName,
            type = typeName,
            languages = langs,
            patterns = patterns,
            servers = servers,
            blacklist = blacklist
        )
    }

    // Invoke a no-arg method, searching up class hierarchy
    private fun Any.invokeR(methodName: String): Any? {
        var cls: Class<*>? = this.javaClass
        while (cls != null) {
            try {
                val m = cls.getDeclaredMethod(methodName)
                m.isAccessible = true
                return m.invoke(this)
            } catch (_: NoSuchMethodException) {
                cls = cls.superclass
            }
        }
        return null
    }

    // Invoke a single-argument setter, matching by method name regardless of exact param type
    private fun Any.invokeRSet(methodName: String, value: Any?) {
        var cls: Class<*>? = this.javaClass
        while (cls != null) {
            val m = cls.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == 1 }
            if (m != null) {
                m.isAccessible = true
                m.invoke(this, value)
                return
            }
            cls = cls.superclass
        }
    }
}

private fun Class<*>.findMethodInHierarchy(name: String): Method? {
    var cls: Class<*>? = this
    while (cls != null) {
        val method = cls.declaredMethods.firstOrNull { it.name == name }
        if (method != null) return method
        cls = cls.superclass
    }
    return null
}
