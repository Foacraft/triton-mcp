package com.foacraft.mcp.triton.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.system.exitProcess

private const val SERVER_KEY = "triton-mcp"
private val prettyJson = Json { prettyPrint = true }

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "--install" -> {
            val target = args.getOrNull(1)
            if (target == null) {
                println("Error: --install requires an <ip:port> argument.")
                println("  Example: java -jar triton-mcp.jar --install 192.168.1.10:25580")
                exitProcess(1)
            }
            install(target)
        }
        "--uninstall" -> uninstall()
        "--help", "-h", null -> printHelp()
        else -> {
            println("Unknown option: ${args[0]}")
            printHelp()
            exitProcess(1)
        }
    }
}

// ---------------------------------------------------------------------------
// Install
// ---------------------------------------------------------------------------

private fun install(target: String) {
    // Normalise: accept "ip", "ip:port", or full URL
    val url = when {
        target.startsWith("http://") || target.startsWith("https://") -> "$target/mcp"
        else -> "http://$target/mcp"
    }

    println("Installing triton-mcp → $url")
    println()

    val claudeCodeResult = writeClaudeCodeConfig(url)
    val desktopResult    = writeClaudeDesktopConfig(url)

    println()
    println(claudeCodeResult)
    println(desktopResult)
    println()
    println("Done. Restart Claude Desktop if it is running.")
}

private fun writeClaudeCodeConfig(url: String): String {
    val file = File(System.getProperty("user.home"), ".claude.json")
    val root = readJsonObject(file)

    val existing = root["mcpServers"]?.jsonObject ?: JsonObject(emptyMap())
    val entry = buildJsonObject {
        put("type", JsonPrimitive("http"))
        put("url",  JsonPrimitive(url))
    }
    val updated = buildJsonObject {
        root.forEach { (k, v) -> if (k != "mcpServers") put(k, v) }
        put("mcpServers", buildJsonObject {
            existing.forEach { (k, v) -> put(k, v) }
            put(SERVER_KEY, entry)
        })
    }

    file.writeText(prettyJson.encodeToString(JsonObject.serializer(), updated))
    return "  [Claude Code]    ${file.absolutePath}  ✓"
}

private fun writeClaudeDesktopConfig(url: String): String {
    val file = claudeDesktopConfigFile() ?: return "  [Claude Desktop] Not supported on this OS (${osName()})"

    val root = readJsonObject(file)
    val existing = root["mcpServers"]?.jsonObject ?: JsonObject(emptyMap())
    val entry = buildJsonObject {
        put("command", JsonPrimitive("npx"))
        put("args", buildJsonArray {
            add(JsonPrimitive("-y"))
            add(JsonPrimitive("mcp-remote"))
            add(JsonPrimitive(url))
            add(JsonPrimitive("--allow-http"))
        })
    }
    val updated = buildJsonObject {
        root.forEach { (k, v) -> if (k != "mcpServers") put(k, v) }
        put("mcpServers", buildJsonObject {
            existing.forEach { (k, v) -> put(k, v) }
            put(SERVER_KEY, entry)
        })
    }

    file.parentFile?.mkdirs()
    file.writeText(prettyJson.encodeToString(JsonObject.serializer(), updated))
    return "  [Claude Desktop] ${file.absolutePath}  ✓"
}

// ---------------------------------------------------------------------------
// Uninstall
// ---------------------------------------------------------------------------

private fun uninstall() {
    println("Uninstalling triton-mcp…")
    println()

    val claudeCodeResult = removeFromClaudeCodeConfig()
    val desktopResult    = removeFromClaudeDesktopConfig()

    println()
    println(claudeCodeResult)
    println(desktopResult)
    println()
    println("Done.")
}

private fun removeFromClaudeCodeConfig(): String {
    val file = File(System.getProperty("user.home"), ".claude.json")
    if (!file.exists()) return "  [Claude Code]    ~/.claude.json not found — skipped"

    val root = readJsonObject(file)
    val existing = root["mcpServers"]?.jsonObject ?: return "  [Claude Code]    No mcpServers entry — skipped"

    if (SERVER_KEY !in existing) return "  [Claude Code]    $SERVER_KEY not found — skipped"

    val updated = buildJsonObject {
        root.forEach { (k, v) -> if (k != "mcpServers") put(k, v) }
        put("mcpServers", buildJsonObject {
            existing.forEach { (k, v) -> if (k != SERVER_KEY) put(k, v) }
        })
    }
    file.writeText(prettyJson.encodeToString(JsonObject.serializer(), updated))
    return "  [Claude Code]    Removed $SERVER_KEY  ✓"
}

private fun removeFromClaudeDesktopConfig(): String {
    val file = claudeDesktopConfigFile()
        ?: return "  [Claude Desktop] Not supported on this OS (${osName()})"
    if (!file.exists()) return "  [Claude Desktop] Config file not found — skipped"

    val root = readJsonObject(file)
    val existing = root["mcpServers"]?.jsonObject ?: return "  [Claude Desktop] No mcpServers entry — skipped"

    if (SERVER_KEY !in existing) return "  [Claude Desktop] $SERVER_KEY not found — skipped"

    val updated = buildJsonObject {
        root.forEach { (k, v) -> if (k != "mcpServers") put(k, v) }
        put("mcpServers", buildJsonObject {
            existing.forEach { (k, v) -> if (k != SERVER_KEY) put(k, v) }
        })
    }
    file.writeText(prettyJson.encodeToString(JsonObject.serializer(), updated))
    return "  [Claude Desktop] Removed $SERVER_KEY  ✓"
}

// ---------------------------------------------------------------------------
// Help
// ---------------------------------------------------------------------------

private fun printHelp() {
    println("""
triton-mcp CLI installer

Usage:
  java -jar triton-mcp.jar --install <ip:port>   Install MCP config for Claude Code and Claude Desktop
  java -jar triton-mcp.jar --uninstall            Remove triton-mcp from all Claude configs
  java -jar triton-mcp.jar --help                 Show this help

Examples:
  java -jar triton-mcp.jar --install 192.168.1.10:25580
  java -jar triton-mcp.jar --install my.server.com:25580
  java -jar triton-mcp.jar --uninstall

Config files written:
  Claude Code    : ~/.claude.json
  Claude Desktop : ~/Library/Application Support/Claude/claude_desktop_config.json  (macOS)
                   %APPDATA%\Claude\claude_desktop_config.json                       (Windows)

Note: If authToken is enabled on the server, edit the config files manually to add the token.
  Claude Code    : add  "headers": {"Authorization": "Bearer <token>"}
  Claude Desktop : append  "--header", "Authorization: Bearer <token>"  to the args array
    """.trimIndent())
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun claudeDesktopConfigFile(): File? {
    val home = System.getProperty("user.home")
    return when {
        System.getProperty("os.name").contains("mac", ignoreCase = true) ->
            File(home, "Library/Application Support/Claude/claude_desktop_config.json")
        System.getProperty("os.name").contains("win", ignoreCase = true) ->
            File(System.getenv("APPDATA") ?: return null, "Claude/claude_desktop_config.json")
        else -> null
    }
}

private fun osName(): String = System.getProperty("os.name") ?: "unknown"

private fun readJsonObject(file: File): JsonObject {
    if (!file.exists() || file.length() == 0L) return JsonObject(emptyMap())
    return try {
        Json.parseToJsonElement(file.readText()).jsonObject
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }
}
