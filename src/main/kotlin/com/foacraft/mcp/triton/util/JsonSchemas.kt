package com.foacraft.mcp.triton.util

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun buildToolInput(block: SchemaBuilder.() -> Unit): Tool.Input {
    val builder = SchemaBuilder()
    builder.block()
    return builder.build()
}

class SchemaBuilder {
    private val properties = mutableMapOf<String, JsonObject>()
    private val required = mutableListOf<String>()

    fun string(name: String, description: String, required: Boolean = false) {
        properties[name] = buildJsonObject {
            put("type", "string")
            put("description", description)
        }
        if (required) this.required.add(name)
    }

    fun int(name: String, description: String, required: Boolean = false, default: Int? = null) {
        properties[name] = buildJsonObject {
            put("type", "integer")
            put("description", description)
            default?.let { put("default", it) }
        }
        if (required) this.required.add(name)
    }

    fun bool(name: String, description: String, required: Boolean = false) {
        properties[name] = buildJsonObject {
            put("type", "boolean")
            put("description", description)
        }
        if (required) this.required.add(name)
    }

    fun stringArray(name: String, description: String, required: Boolean = false) {
        properties[name] = buildJsonObject {
            put("type", "array")
            put("description", description)
            putJsonObject("items") { put("type", "string") }
        }
        if (required) this.required.add(name)
    }

    fun stringMap(name: String, description: String, required: Boolean = false) {
        properties[name] = buildJsonObject {
            put("type", "object")
            put("description", description)
            putJsonObject("additionalProperties") { put("type", "string") }
        }
        if (required) this.required.add(name)
    }

    fun arrayOfObjects(name: String, description: String, required: Boolean = false, itemBlock: SchemaBuilder.() -> Unit) {
        val itemBuilder = SchemaBuilder()
        itemBuilder.itemBlock()
        properties[name] = buildJsonObject {
            put("type", "array")
            put("description", description)
            putJsonObject("items") {
                put("type", "object")
                putJsonObject("properties") {
                    for ((k, v) in itemBuilder.properties) {
                        put(k, v)
                    }
                }
                putJsonArray("required") {
                    itemBuilder.required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            }
        }
        if (required) this.required.add(name)
    }

    fun build(): Tool.Input {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                for ((k, v) in properties) put(k, v)
            }
            putJsonArray("required") {
                required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            }
        }
        return Tool.Input(schema)
    }
}
