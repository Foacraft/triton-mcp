package com.foacraft.mcp.triton.mcp

import com.foacraft.mcp.triton.config.PluginConfig
import com.foacraft.mcp.triton.mcp.tools.registerCollectionTools
import com.foacraft.mcp.triton.mcp.tools.registerItemReadTools
import com.foacraft.mcp.triton.mcp.tools.registerItemWriteTools
import com.foacraft.mcp.triton.mcp.tools.registerLanguageTools
import com.foacraft.mcp.triton.triton.TritonBridge
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import org.slf4j.Logger
import java.net.BindException

class McpServer(
    private val config: PluginConfig,
    private val bridge: TritonBridge,
    private val logger: Logger
) {
    private var engine: EmbeddedServer<*, *>? = null

    fun start() {
        try {
            engine = embeddedServer(CIO, host = config.host, port = config.port) {
                install(ContentNegotiation) {
                    json(McpJson)
                }
                if (config.authToken != null) {
                    install(AuthTokenPlugin) {
                        token = config.authToken
                    }
                }
                mcpStreamableHttp(path = "/mcp") {
                    buildMcpServer()
                }
            }.start(wait = false)
            logger.info("Triton MCP server started on {}:{}", config.host, config.port)
        } catch (e: BindException) {
            logger.error("Failed to start MCP server: port {} is already in use.", config.port)
            engine = null
        } catch (e: Exception) {
            logger.error("Failed to start MCP server: {}", e.message)
            engine = null
        }
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        engine = null
        logger.info("Triton MCP server stopped.")
    }

    private fun buildMcpServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = config.serverName,
                version = config.serverVersion
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))
            )
        )

        server.registerLanguageTools(bridge)
        server.registerCollectionTools(bridge)
        server.registerItemReadTools(bridge)
        server.registerItemWriteTools(bridge)

        return server
    }
}
