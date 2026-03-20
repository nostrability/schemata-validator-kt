package nostrability.schemata.validator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import nostrability.schemata.Schemata

/**
 * A single validation error or warning.
 */
data class ValidationError(
    val instancePath: String,
    val keyword: String,
    val message: String,
    val schemaPath: String,
)

/**
 * Result of validating data against a JSON schema.
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<ValidationError>,
)

/**
 * Subject of a protocol message.
 */
enum class Subject {
    RELAY,
    CLIENT;

    override fun toString(): String = name.lowercase()
}

/**
 * Nostr JSON Schema validator.
 *
 * Validates Nostr events, NIP-11 documents, and protocol messages
 * against the canonical [schemata](https://github.com/nostrability/schemata) definitions
 * using Draft-07 JSON Schema via networknt/json-schema-validator.
 *
 * This is designed for CI and integration tests, not production hot paths.
 */
object SchemataValidator {

    private val mapper = ObjectMapper()
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

    /**
     * Validate data against a JSON schema.
     *
     * Key behaviors:
     * 1. Strips nested $id from schema copy (prevents resolution issues)
     * 2. Draft-07 validation
     * 3. Custom errorMessage enrichment
     * 4. Additional properties reported as warnings (not when additionalProperties: false)
     */
    fun validate(schema: JsonElement, data: JsonElement): ValidationResult {
        val schemaNode = toJackson(schema)
        val dataNode = toJackson(data)

        // Strip nested $id fields (depth > 0)
        stripNestedIds(schemaNode, 0)

        val jsonSchema: JsonSchema = try {
            factory.getSchema(schemaNode)
        } catch (e: Exception) {
            return ValidationResult(
                valid = false,
                errors = listOf(
                    ValidationError(
                        instancePath = "",
                        keyword = "compilation",
                        message = "Schema compilation error: ${e.message}",
                        schemaPath = "",
                    )
                ),
                warnings = emptyList(),
            )
        }

        val messages: Set<ValidationMessage> = jsonSchema.validate(dataNode)

        val errors = messages.map { msg ->
            val enriched = ErrorMessages.enrich(schemaNode, msg)
            ValidationError(
                instancePath = msg.instanceLocation?.toString() ?: "",
                keyword = msg.type ?: "unknown",
                message = enriched,
                schemaPath = msg.schemaLocation?.toString() ?: "",
            )
        }

        val warnings = AdditionalProps.collect(toJackson(schema), dataNode, "")

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    /**
     * Validate a Nostr event by kind. Looks up `kind{N}Schema`.
     */
    fun validateNote(event: JsonElement): ValidationResult {
        val obj = event as? JsonObject
        val kindElement = obj?.get("kind")
        val kind = (kindElement as? JsonPrimitive)?.intOrNull

        if (kind == null) {
            return ValidationResult(
                valid = false,
                errors = listOf(
                    ValidationError(
                        instancePath = "",
                        keyword = "note",
                        message = "Event missing 'kind' field",
                        schemaPath = "",
                    )
                ),
                warnings = emptyList(),
            )
        }

        val key = "kind${kind}Schema"
        val schema = Schemata.get(key) ?: return ValidationResult(
            valid = false,
            errors = emptyList(),
            warnings = listOf(
                ValidationError(
                    instancePath = "",
                    keyword = "note",
                    message = "No schema found for kind $kind",
                    schemaPath = "",
                )
            ),
        )

        return validate(schema, event)
    }

    /**
     * Validate a NIP-11 relay information document.
     */
    fun validateNip11(doc: JsonElement): ValidationResult {
        val schema = Schemata.get("nip11Schema") ?: return ValidationResult(
            valid = false,
            errors = listOf(
                ValidationError(
                    instancePath = "",
                    keyword = "nip11",
                    message = "nip11Schema not found in registry",
                    schemaPath = "",
                )
            ),
            warnings = emptyList(),
        )

        return validate(schema, doc)
    }

    /**
     * Validate a protocol message (relay or client).
     * Constructs key as `{subject}{Slug}Schema`, e.g. `relayNoticeSchema`.
     */
    fun validateMessage(msg: JsonElement, subject: Subject, slug: String): ValidationResult {
        val capitalized = slug.lowercase().replaceFirstChar { it.uppercase() }
        val key = "${subject}${capitalized}Schema"

        val schema = Schemata.get(key) ?: return ValidationResult(
            valid = false,
            errors = emptyList(),
            warnings = listOf(
                ValidationError(
                    instancePath = "",
                    keyword = "message",
                    message = "No schema found for $subject $slug",
                    schemaPath = "",
                )
            ),
        )

        return validate(schema, msg)
    }

    /**
     * Look up a schema by key from the data package.
     */
    fun getSchema(key: String): JsonElement? = Schemata.get(key)

    // --- Internal helpers ---

    /**
     * Strip nested `$id` fields from a schema (except root level).
     * Prevents json-schema-validator from trying to resolve nested schemas as independent documents.
     */
    private fun stripNestedIds(node: JsonNode, depth: Int) {
        when {
            node.isObject -> {
                val obj = node as ObjectNode
                if (depth > 0) {
                    obj.remove("\$id")
                }
                // Collect field values before iterating to avoid concurrent modification
                val children = obj.fields().asSequence().map { it.value }.toList()
                children.forEach { value ->
                    stripNestedIds(value, depth + 1)
                }
            }
            node.isArray -> {
                node.forEach { child ->
                    stripNestedIds(child, depth + 1)
                }
            }
        }
    }

    /**
     * Convert a kotlinx.serialization JsonElement to a Jackson JsonNode.
     */
    internal fun toJackson(element: JsonElement): JsonNode {
        val text = element.toString()
        return mapper.readTree(text)
    }
}
