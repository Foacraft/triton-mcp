package com.foacraft.mcp.triton

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import com.foacraft.mcp.triton.config.PluginConfig
import com.foacraft.mcp.triton.mcp.McpServer
import com.foacraft.mcp.triton.triton.TritonBridge
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Plugin(
    id = "triton-mcp",
    name = "Triton MCP",
    version = "1.0.0",
    description = "MCP server for managing Triton translations via AI clients",
    authors = ["foacraft"],
    dependencies = [Dependency(id = "triton")]
)
class TritonMcpPlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path
) {
    private var bridge: TritonBridge? = null
    private var mcpServer: McpServer? = null

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        val config = loadConfig()

        val b = TritonBridge(logger)
        try {
            b.initialize()
        } catch (e: Exception) {
            logger.error("Failed to initialize Triton bridge: {}. MCP server will NOT start.", e.message)
            return
        }
        bridge = b

        val mcp = McpServer(config, b, logger)
        mcp.start()
        mcpServer = mcp
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        mcpServer?.stop()
    }

    private fun loadConfig(): PluginConfig {
        dataDirectory.createDirectories()
        val configFile = dataDirectory.resolve("config.yml")

        if (!configFile.exists()) {
            val default = javaClass.getResourceAsStream("/config.yml")?.bufferedReader()?.readText()
                ?: Yaml.default.encodeToString(PluginConfig.serializer(), PluginConfig())
            configFile.writeText(default)
            logger.info("Generated default config at {}", configFile)
        }

        return try {
            val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))
            yaml.decodeFromString(PluginConfig.serializer(), configFile.readText())
        } catch (e: Exception) {
            logger.warn("Failed to parse config.yml ({}), using defaults.", e.message)
            PluginConfig()
        }
    }
}
