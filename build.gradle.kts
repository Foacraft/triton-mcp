plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.foacraft"
version = "1.2.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Velocity proxy API — provided at runtime
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    // Triton API + core internals — provided at runtime (same server JVM)
    compileOnly(files("libs/triton-api.jar"))
    compileOnly(files("libs/triton-core.jar"))

    // MCP Kotlin SDK (Streamable HTTP transport)
    implementation("io.modelcontextprotocol:kotlin-sdk:0.9.0")

    // Ktor CIO engine + SSE + Content Negotiation (required by Streamable HTTP transport)
    implementation("io.ktor:ktor-server-cio:3.2.3")
    implementation("io.ktor:ktor-server-sse:3.2.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.2.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")

    // YAML config
    implementation("com.charleskorn.kaml:kaml:0.65.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.processResources {
    filesMatching("velocity-plugin.json") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "com.foacraft.mcp.triton.cli.CliInstallerKt"
    }
    // Relocate all bundled deps to avoid conflicts with other Velocity plugins
    relocate("io.ktor",                     "com.foacraft.mcp.triton.shadow.ktor")
    relocate("io.github.oshai",             "com.foacraft.mcp.triton.shadow.oshai")
    relocate("io.modelcontextprotocol",     "com.foacraft.mcp.triton.shadow.mcp")
    relocate("com.charleskorn.kaml",        "com.foacraft.mcp.triton.shadow.kaml")
    relocate("kotlinx.serialization",       "com.foacraft.mcp.triton.shadow.serialization")
    relocate("kotlinx.coroutines",          "com.foacraft.mcp.triton.shadow.coroutines")
    relocate("io.netty",                    "com.foacraft.mcp.triton.shadow.netty")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
