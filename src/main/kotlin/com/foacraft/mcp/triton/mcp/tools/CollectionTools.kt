package com.foacraft.mcp.triton.mcp.tools

import com.foacraft.mcp.triton.triton.TritonBridge
import com.foacraft.mcp.triton.util.buildToolInput
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun Server.registerCollectionTools(bridge: TritonBridge) {

    addTool(
        name = "list_collections",
        description = """List all translation collections. A collection is a named group of translation items, typically
            | used to organise texts by plugin or feature (e.g. "survival", "lobby", "shop"). The built-in
            | collection is "default". Each collection can also carry a server filter: either a whitelist or
            | blacklist of backend server names that determines which servers receive those translations.""".trimMargin(),
        inputSchema = buildToolInput {}
    ) { _ ->
        val collections = bridge.listCollections()
        val json = buildJsonArray {
            for (col in collections) {
                add(buildJsonObject {
                    put("name", col.name)
                    put("itemCount", col.itemCount)
                    put("blacklist", col.blacklist)
                    putJsonArray("servers") { col.servers.forEach { add(JsonPrimitive(it)) } }
                })
            }
        }
        CallToolResult(content = listOf(TextContent(json.toString())))
    }

    addTool(
        name = "get_collection_items",
        description = """Get all translation items inside a specific collection.
            | A collection groups related translation keys together (e.g. all texts for one plugin or feature).
            | Use 'type' to restrict results to text items or sign items.""".trimMargin(),
        inputSchema = buildToolInput {
            string("collection", "Name of the collection (e.g. 'default', 'survival', 'shop').", required = true)
            string("type", "Optional filter: 'text' for message translations, 'sign' for in-world sign translations.")
        }
    ) { request ->
        val args = request.arguments ?: return@addTool CallToolResult(
            isError = true, content = listOf(TextContent("No parameters provided"))
        )

        val collectionName = args["collection"]?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent("Missing required parameter: collection")))
        val typeFilter = args["type"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

        val items = bridge.getCollectionItems(collectionName, typeFilter)
        val json = buildJsonArray {
            for (item in items) add(itemSummaryToJson(item))
        }
        CallToolResult(content = listOf(TextContent(json.toString())))
    }
}

internal fun itemSummaryToJson(item: TritonBridge.ItemSummary) = buildJsonObject {
    put("key", item.key)
    put("collection", item.collection)
    put("type", item.type)
    putJsonObject("languages") {
        item.languages.forEach { (lang, text) -> put(lang, text) }
    }
    if (item.patterns.isNotEmpty()) {
        putJsonArray("patterns") { item.patterns.forEach { add(JsonPrimitive(it)) } }
    }
    item.servers?.let { servers ->
        putJsonArray("servers") { servers.forEach { add(JsonPrimitive(it)) } }
        item.blacklist?.let { put("blacklist", it) }
    }
}
