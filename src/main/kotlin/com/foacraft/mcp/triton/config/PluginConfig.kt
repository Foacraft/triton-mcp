package com.foacraft.mcp.triton.config

import kotlinx.serialization.Serializable

@Serializable
data class PluginConfig(
    val port: Int = 25580,
    val host: String = "0.0.0.0",
    val authToken: String? = null,
    val serverName: String = "triton-mcp",
    val serverVersion: String = "1.0.0"
)
