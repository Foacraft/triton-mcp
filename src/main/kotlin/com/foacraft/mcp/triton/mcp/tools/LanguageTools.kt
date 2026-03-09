package com.foacraft.mcp.triton.mcp.tools

import com.foacraft.mcp.triton.triton.TritonBridge
import com.foacraft.mcp.triton.util.buildToolInput
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Server.registerLanguageTools(bridge: TritonBridge) {
    addTool(
        name = "list_languages",
        description = "List all languages configured in Triton. The 'name' field is the language identifier used as a key in translation maps. Always call this first to know which language names to use when writing translations.",
        inputSchema = Tool.Input()
    ) { _ ->
        val langs = bridge.getLanguages()
        val json = buildJsonArray {
            for (lang in langs) {
                add(buildJsonObject {
                    put("name", lang.name)
                    put("displayName", lang.displayName)
                    put("isMain", lang.isMain)
                })
            }
        }
        CallToolResult(content = listOf(TextContent(json.toString())))
    }
}
