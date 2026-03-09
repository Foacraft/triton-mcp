package com.foacraft.mcp.triton.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal const val SERVER_KEY = "triton-mcp"
internal val prettyJson = Json { prettyPrint = true }

// ---------------------------------------------------------------------------
// URL normalisation
// ---------------------------------------------------------------------------

/** Accepts "ip:port", "ip", or a full URL; always returns "http://…/mcp". */
internal fun normaliseUrl(target: String): String = when {
    target.startsWith("http://") || target.startsWith("https://") ->
        if (target.endsWith("/mcp")) target else "$target/mcp"
    else -> "http://$target/mcp"
}

/** Strips "http://" prefix and "/mcp" suffix to get back a bare "ip:port". */
internal fun urlToAddress(url: String): String =
    url.removePrefix("http://").removePrefix("https://").removeSuffix("/mcp")

// ---------------------------------------------------------------------------
// Read current installation state
// ---------------------------------------------------------------------------

data class InstallStatus(
    val claudeCodeUrl: String?,     // null = not installed
    val claudeDesktopUrl: String?,  // null = not installed or not supported
    val desktopSupported: Boolean
) {
    val isInstalled get() = claudeCodeUrl != null || claudeDesktopUrl != null
    /** Best guess at the installed address (prefers Claude Code entry). */
    val installedAddress get() = (claudeCodeUrl ?: claudeDesktopUrl)?.let { urlToAddress(it) }
}

internal fun readInstallStatus(): InstallStatus {
    val codeUrl = runCatching {
        val file = File(System.getProperty("user.home"), ".claude.json")
        val root = readJsonObject(file)
        root["mcpServers"]?.jsonObject?.get(SERVER_KEY)
            ?.jsonObject?.get("url")?.jsonPrimitive?.content
    }.getOrNull()

    val desktopFile = claudeDesktopConfigFile()
    val desktopUrl = if (desktopFile != null) runCatching {
        val root = readJsonObject(desktopFile)
        // args: ["-y", "mcp-remote", "<url>", "--allow-http"]
        root["mcpServers"]?.jsonObject?.get(SERVER_KEY)
            ?.jsonObject?.get("args")?.jsonArray?.getOrNull(2)?.jsonPrimitive?.content
    }.getOrNull() else null

    return InstallStatus(
        claudeCodeUrl    = codeUrl,
        claudeDesktopUrl = desktopUrl,
        desktopSupported = desktopFile != null
    )
}

// ---------------------------------------------------------------------------
// Install
// ---------------------------------------------------------------------------

internal fun writeClaudeCodeConfig(url: String): String {
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
    return "[Claude Code]    ${file.absolutePath}  ✓"
}

internal fun writeClaudeDesktopConfig(url: String): String {
    val file = claudeDesktopConfigFile()
        ?: return "[Claude Desktop] Not supported on this OS (${System.getProperty("os.name")})"
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
    return "[Claude Desktop] ${file.absolutePath}  ✓"
}

// ---------------------------------------------------------------------------
// Uninstall
// ---------------------------------------------------------------------------

internal fun removeFromClaudeCodeConfig(): String {
    val file = File(System.getProperty("user.home"), ".claude.json")
    if (!file.exists()) return "[Claude Code]    ~/.claude.json not found — skipped"
    val root = readJsonObject(file)
    val existing = root["mcpServers"]?.jsonObject
        ?: return "[Claude Code]    No mcpServers entry — skipped"
    if (SERVER_KEY !in existing) return "[Claude Code]    $SERVER_KEY not found — skipped"
    val updated = buildJsonObject {
        root.forEach { (k, v) -> if (k != "mcpServers") put(k, v) }
        put("mcpServers", buildJsonObject {
            existing.forEach { (k, v) -> if (k != SERVER_KEY) put(k, v) }
        })
    }
    file.writeText(prettyJson.encodeToString(JsonObject.serializer(), updated))
    return "[Claude Code]    Removed $SERVER_KEY  ✓"
}

internal fun removeFromClaudeDesktopConfig(): String {
    val file = claudeDesktopConfigFile()
        ?: return "[Claude Desktop] Not supported on this OS (${System.getProperty("os.name")})"
    if (!file.exists()) return "[Claude Desktop] Config file not found — skipped"
    val root = readJsonObject(file)
    val existing = root["mcpServers"]?.jsonObject
        ?: return "[Claude Desktop] No mcpServers entry — skipped"
    if (SERVER_KEY !in existing) return "[Claude Desktop] $SERVER_KEY not found — skipped"
    val updated = buildJsonObject {
        root.forEach { (k, v) -> if (k != "mcpServers") put(k, v) }
        put("mcpServers", buildJsonObject {
            existing.forEach { (k, v) -> if (k != SERVER_KEY) put(k, v) }
        })
    }
    file.writeText(prettyJson.encodeToString(JsonObject.serializer(), updated))
    return "[Claude Desktop] Removed $SERVER_KEY  ✓"
}

// ---------------------------------------------------------------------------
// Connectivity test
// ---------------------------------------------------------------------------

/** Returns (success, message). Sends an MCP initialize request and checks for HTTP 200. */
internal fun testConnection(url: String): Pair<Boolean, String> {
    return try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout    = 5_000
        conn.requestMethod  = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json, text/event-stream")
        conn.doOutput = true
        val body = """{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"triton-mcp-installer","version":"1"}}}"""
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        if (code == 200) {
            Pair(true, "Connected — HTTP $code ✓")
        } else {
            Pair(false, "HTTP $code — unexpected response")
        }
    } catch (e: java.net.ConnectException) {
        Pair(false, "Connection refused — server not reachable at $url")
    } catch (e: java.net.SocketTimeoutException) {
        Pair(false, "Timeout — server did not respond within 5 s")
    } catch (e: Exception) {
        Pair(false, "Error: ${e.message}")
    }
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

internal fun claudeDesktopConfigFile(): File? {
    val home = System.getProperty("user.home")
    val os   = System.getProperty("os.name") ?: ""
    return when {
        os.contains("mac", ignoreCase = true) ->
            File(home, "Library/Application Support/Claude/claude_desktop_config.json")
        os.contains("win", ignoreCase = true) ->
            File(System.getenv("APPDATA") ?: return null, "Claude/claude_desktop_config.json")
        else -> null
    }
}

internal fun readJsonObject(file: File): JsonObject {
    if (!file.exists() || file.length() == 0L) return JsonObject(emptyMap())
    return try {
        Json.parseToJsonElement(file.readText()).jsonObject
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }
}
