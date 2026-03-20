package nostrability.schemata.validator

import com.fasterxml.jackson.databind.JsonNode

/**
 * Recursively detect additional properties in data not described by the schema.
 * Returns warnings (not errors) for extra properties, unless `additionalProperties: false`
 * is set (in which case the JSON Schema validator handles it as an error).
 */
object AdditionalProps {

    /**
     * Collect additional property warnings by comparing data keys against
     * the schema's declared `properties` and `patternProperties`.
     */
    fun collect(schema: JsonNode, data: JsonNode, path: String): List<ValidationError> {
        if (!data.isObject || !schema.isObject) {
            return emptyList()
        }

        val schemaType = schema.get("type")?.asText()
        if (schemaType != "object") {
            return emptyList()
        }

        // If additionalProperties is explicitly false, the validator handles errors.
        // Don't duplicate as warnings.
        val additionalProps = schema.get("additionalProperties")
        if (additionalProps != null && additionalProps.isBoolean && !additionalProps.asBoolean()) {
            return emptyList()
        }

        val warnings = mutableListOf<ValidationError>()

        // Collect explicitly declared properties
        val allowed = mutableSetOf<String>()
        val properties = schema.get("properties")
        if (properties != null && properties.isObject) {
            properties.fieldNames().forEach { allowed.add(it) }
        }

        // Collect keys matched by patternProperties
        val patternProperties = schema.get("patternProperties")
        if (patternProperties != null && patternProperties.isObject) {
            patternProperties.fieldNames().forEach { pattern ->
                try {
                    val regex = Regex(pattern)
                    data.fieldNames().forEach { key ->
                        if (regex.containsMatchIn(key)) {
                            allowed.add(key)
                        }
                    }
                } catch (_: Exception) {
                    // Invalid regex pattern — skip
                }
            }
        }

        // Warn about keys not in allowed set
        data.fieldNames().forEach { key ->
            if (key !in allowed) {
                warnings.add(
                    ValidationError(
                        instancePath = path,
                        keyword = "additionalProperties",
                        message = "additional property \"$key\" exists",
                        schemaPath = "",
                    )
                )
            }
        }

        // Recurse into nested object properties
        if (properties != null && properties.isObject) {
            properties.fieldNames().forEach { prop ->
                val propSchema = properties.get(prop)
                val propData = data.get(prop)
                if (propSchema != null && propData != null && propData.isObject) {
                    val childPath = "$path/$prop"
                    warnings.addAll(collect(propSchema, propData, childPath))
                }
            }
        }

        return warnings
    }
}
