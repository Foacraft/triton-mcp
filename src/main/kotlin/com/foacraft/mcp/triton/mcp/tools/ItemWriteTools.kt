package com.foacraft.mcp.triton.mcp.tools

import com.foacraft.mcp.triton.triton.TritonBridge
import com.foacraft.mcp.triton.util.buildToolInput
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

fun Server.registerItemWriteTools(bridge: TritonBridge) {

    addTool(
        name = "create_text_item",
        description = """Create a new text translation item inside a collection.
            | A collection groups related keys (e.g. all texts for one plugin). Use "default" when unsure.
            | Fails if the key already exists — use update_item_translations or batch_upsert_items instead.
            | 'servers' and 'blacklist' are optional Triton server-filter fields: when set, Triton only applies
            | this translation to the listed backend servers (whitelist) or to all servers except the listed ones (blacklist).""".trimMargin(),
        inputSchema = buildToolInput {
            string("key", "Unique translation key using dot notation (e.g. 'messages.welcome', 'shop.button.buy').", required = true)
            string("collection", "Collection to add the item to. Use 'default' if you are unsure.", required = true)
            stringMap("translations", "Map of Triton language name → translated text (e.g. {\"en_GB\": \"Hello\", \"zh_CN\": \"你好\"}).", required = true)
            stringArray("servers", "Optional: backend server names this item applies to (used with 'blacklist' to filter servers).")
            bool("blacklist", "Optional: when true, 'servers' is a blacklist (exclude those servers); when false, it's a whitelist.")
        }
    ) { request ->
        val args = request.arguments ?: return@addTool CallToolResult(
            isError = true, content = listOf(TextContent(text = "No parameters provided"))
        )

        val key = args["key"]?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent(text = "Missing required parameter: key")))
        val collection = args["collection"]?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent(text = "Missing required parameter: collection")))
        val translations = args["translations"]?.jsonObject?.let { parseStringMap(it) }
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent(text = "Missing required parameter: translations")))

        if (bridge.findItemByKey(key) != null) {
            return@addTool CallToolResult(
                isError = true,
                content = listOf(TextContent(text = "Item '$key' already exists. Use update_item_translations to modify it."))
            )
        }

        val servers = args["servers"]?.jsonArray?.map { it.jsonPrimitive.content }
        val blacklist = args["blacklist"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()

        bridge.upsertTextItem(collection, key, translations, servers, blacklist)
        CallToolResult(content = listOf(TextContent(text = "Created item '$key' in collection '$collection'.")))
    }

    addTool(
        name = "update_item_translations",
        description = """Update the translations of an existing text item.
            | Only the language entries you provide are updated; all other languages in the item are preserved.
            | Use this for targeted fixes or adding a single missing language to an item.""".trimMargin(),
        inputSchema = buildToolInput {
            string("key", "The translation key to update.", required = true)
            stringMap("translations", "Language name → new text. Only these entries are written; other languages are untouched.", required = true)
        }
    ) { request ->
        val args = request.arguments ?: return@addTool CallToolResult(
            isError = true, content = listOf(TextContent(text = "No parameters provided"))
        )

        val key = args["key"]?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent(text = "Missing required parameter: key")))
        val translations = args["translations"]?.jsonObject?.let { parseStringMap(it) }
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent(text = "Missing required parameter: translations")))

        val existing = bridge.findItemByKey(key)
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent(text = "Item not found: $key")))
        if (existing.type != "text") {
            return@addTool CallToolResult(
                isError = true,
                content = listOf(TextContent(text = "Item '$key' is of type '${existing.type}'. Only text items can be updated with this tool."))
            )
        }

        bridge.upsertTextItem(existing.collection, key, translations)
        val updatedLangs = translations.keys.joinToString(", ")
        CallToolResult(content = listOf(TextContent(text = "Updated '$key' — languages written: $updatedLangs.")))
    }

    addTool(
        name = "delete_item",
        description = "Permanently delete a translation item by key. The change is persisted immediately. Call reload_triton to apply the removal to live players.",
        inputSchema = buildToolInput {
            string("key", "The translation key to delete.", required = true)
        }
    ) { request ->
        val key = request.arguments?.get("key")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent(text = "Missing required parameter: key")))

        val deleted = bridge.deleteItem(key)
        if (deleted) {
            CallToolResult(content = listOf(TextContent(text = "Deleted item '$key'.")))
        } else {
            CallToolResult(isError = true, content = listOf(TextContent(text = "Item not found: $key")))
        }
    }

    addTool(
        name = "batch_upsert_items",
        description = """Create or update multiple text translation items in one operation. Preferred over repeated single calls.
            | For each item: if the key already exists, only the provided languages are merged in (other languages preserved).
            | If the key does not exist, a new item is created.
            | All changes are persisted in a single write. Call reload_triton afterwards to apply to live players.""".trimMargin(),
        inputSchema = buildToolInput {
            string("collection", "Collection to write items into. Use 'default' if you are unsure, or the collection name returned by list_collections.", required = true)
            arrayOfObjects("items", "Items to create or update.", required = true) {
                string("key", "Unique translation key.", required = true)
                stringMap("translations", "Language name → translated text. Merged into existing translations.", required = true)
            }
        }
    ) { request ->
        val args = request.arguments ?: return@addTool CallToolResult(
            isError = true, content = listOf(TextContent(text = "No parameters provided"))
        )

        val collection = args["collection"]?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent(text = "Missing required parameter: collection")))
        val rawItems = args["items"]?.jsonArray
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent(text = "Missing required parameter: items")))

        val itemList = rawItems.map { element ->
            val obj = element.jsonObject
            buildMap<String, Any?> {
                put("key", obj["key"]?.jsonPrimitive?.content)
                put("translations", obj["translations"]?.jsonObject?.let { parseStringMap(it) })
            }
        }

        val result = bridge.batchUpsertTextItems(collection, itemList)
        val json = buildJsonObject {
            put("created", result.created)
            put("updated", result.updated)
            put("total", result.created + result.updated)
            putJsonArray("errors") { result.errors.forEach { add(JsonPrimitive(it)) } }
        }
        CallToolResult(content = listOf(TextContent(text = json.toString())))
    }

    addTool(
        name = "reload_triton",
        description = """Reload Triton so that all pending translation changes take effect for online players immediately.
            | Call this after any write operations (create, update, delete, batch_upsert) once you are satisfied with the changes.""".trimMargin(),
        inputSchema = buildToolInput {}
    ) { _ ->
        try {
            bridge.reloadTriton()
            CallToolResult(content = listOf(TextContent(text = "Triton reloaded successfully. All translation changes are now live.")))
        } catch (e: Exception) {
            CallToolResult(isError = true, content = listOf(TextContent(text = "Triton reload failed: ${e.message}")))
        }
    }
}

private fun parseStringMap(obj: JsonObject): Map<String, String> =
    obj.entries.associate { (k, v) -> k to v.jsonPrimitive.content }
