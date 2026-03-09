package com.foacraft.mcp.triton.mcp.tools

import com.foacraft.mcp.triton.triton.TritonBridge
import com.foacraft.mcp.triton.util.buildToolInput
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

fun Server.registerItemReadTools(bridge: TritonBridge) {

    addTool(
        name = "get_item",
        description = "Get a specific translation item by its key. Searches across all collections and returns its full translation map.",
        inputSchema = buildToolInput {
            string("key", "The unique translation key to look up (e.g. 'messages.welcome').", required = true)
        }
    ) { request ->
        val key = request.arguments?.get("key")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent(text = "Missing required parameter: key")))

        val item = bridge.findItemByKey(key)
            ?: return@addTool CallToolResult(isError = true, content = listOf(TextContent(text = "Item not found: $key")))

        CallToolResult(content = listOf(TextContent(text = itemSummaryToJson(item).toString())))
    }

    addTool(
        name = "search_items",
        description = """Search and filter translation items across collections. All parameters are optional and combinable.
            | Typical AI workflow: use 'missingLanguage' to discover which items still need translation,
            | then use batch_upsert_items to fill them in.""".trimMargin(),
        inputSchema = buildToolInput {
            string("keyPattern", "Regex pattern matched against item keys (case-insensitive). E.g. '^messages\\.' to find all message keys.")
            string("contentQuery", "Substring searched in any translation value (case-insensitive). Useful to locate duplicates.")
            string("missingLanguage", "Language name (as configured in Triton) to find items that have no translation for that language yet.")
            string("collection", "Restrict search to a single collection. Omit to search all collections.")
            int("limit", "Maximum number of results returned. Defaults to 50.", default = 50)
        }
    ) { request ->
        val args = request.arguments

        fun str(k: String): String? = args?.get(k)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        fun int(k: String, default: Int) = args?.get(k)?.jsonPrimitive?.content?.toIntOrNull() ?: default

        val results = bridge.searchItems(
            keyPattern = str("keyPattern"),
            contentQuery = str("contentQuery"),
            missingLanguage = str("missingLanguage"),
            collection = str("collection"),
            limit = int("limit", 50)
        )

        val json = buildJsonObject {
            put("count", results.size)
            put("items", buildJsonArray {
                results.forEach { add(itemSummaryToJson(it)) }
            })
        }
        CallToolResult(content = listOf(TextContent(text = json.toString())))
    }
}
