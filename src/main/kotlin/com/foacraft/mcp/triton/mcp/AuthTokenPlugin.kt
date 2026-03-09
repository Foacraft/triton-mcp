package com.foacraft.mcp.triton.mcp

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.header
import io.ktor.server.response.respond

class AuthTokenConfig {
    var token: String = ""
}

val AuthTokenPlugin = createApplicationPlugin("AuthTokenPlugin", ::AuthTokenConfig) {
    val expectedToken = pluginConfig.token

    onCall { call ->
        val authHeader = call.request.header("Authorization")
        if (authHeader == null || authHeader != "Bearer $expectedToken") {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized: valid Bearer token required.")
            return@onCall
        }
    }
}
