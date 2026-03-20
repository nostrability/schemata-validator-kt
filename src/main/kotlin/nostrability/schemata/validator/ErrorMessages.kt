package nostrability.schemata.validator

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.ValidationMessage

/**
 * Custom error message enrichment.
 *
 * Tries to find a custom `errorMessage` field in the schema at or near
 * the error's schema path. Checks the node at the schema path, then parent,
 * then grandparent. If found, returns the custom message; otherwise returns
 * the default validation error message.
 */
object ErrorMessages {

    /**
     * Try to enrich a validation message with a custom errorMessage from the schema.
     */
    fun enrich(schema: JsonNode, msg: ValidationMessage): String {
        val defaultMsg = msg.message ?: "validation error"
        val schemaPath = msg.schemaLocation?.toString() ?: return defaultMsg

        // Extract the fragment part after '#' if present (e.g. "https://...#/properties/kind/type")
        val fragment = if ("#" in schemaPath) {
            schemaPath.substringAfter("#")
        } else {
            schemaPath
        }

        // Parse schema path segments (e.g. "/properties/kind/type" -> ["properties", "kind", "type"])
        val cleaned = fragment.split("/").filter { it.isNotEmpty() }

        // Try at node level, then parent, then grandparent
        for (depth in 0..2) {
            if (depth > cleaned.size) break
            val checkSegments = cleaned.subList(0, cleaned.size - depth)
            val node = walkSchema(schema, checkSegments)
            if (node != null && node.isObject) {
                val errorMsg = node.get("errorMessage")
                if (errorMsg != null && errorMsg.isTextual) {
                    return errorMsg.asText()
                }
            }
        }

        return defaultMsg
    }

    /**
     * Walk into a schema following the given path segments.
     */
    private fun walkSchema(schema: JsonNode, segments: List<String>): JsonNode? {
        var current: JsonNode = schema
        for (seg in segments) {
            current = current.get(seg) ?: return null
        }
        return current
    }
}
