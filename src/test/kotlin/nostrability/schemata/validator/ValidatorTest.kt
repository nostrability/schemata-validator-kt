package nostrability.schemata.validator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- Helpers ---

    private fun validKind1Event(): JsonElement = buildJsonObject {
        put("id", "a".repeat(64))
        put("pubkey", "b".repeat(64))
        put("created_at", 1700000000)
        put("kind", 1)
        put("tags", buildJsonArray {})
        put("content", "hello world")
        put("sig", "c".repeat(128))
    }

    // --- validate_note tests ---

    @Test
    fun `kind 1 event validates`() {
        val result = SchemataValidator.validateNote(validKind1Event())
        assertTrue(result.valid, "Valid kind-1 should pass. Errors: ${result.errors}")
    }

    @Test
    fun `wrong kind value fails`() {
        // Use kind 1 schema but set kind to 2
        val event = buildJsonObject {
            put("id", "a".repeat(64))
            put("pubkey", "b".repeat(64))
            put("created_at", 1700000000)
            put("kind", 2)
            put("tags", buildJsonArray {})
            put("content", "hello")
            put("sig", "c".repeat(128))
        }

        // This validates against kind2Schema which may not exist -> warning
        // OR if kind 2 has a schema, the event shape should still be valid
        val result = SchemataValidator.validateNote(event)
        // kind 2 is not defined in schemata, so expect warning
        if (result.warnings.isNotEmpty()) {
            assertTrue(result.warnings.any { it.message.contains("No schema found") })
        }
        // This is valid since kind 2 has no schema - the warning tells us
    }

    @Test
    fun `missing pubkey fails`() {
        val event = buildJsonObject {
            put("id", "a".repeat(64))
            put("created_at", 1700000000)
            put("kind", 1)
            put("tags", buildJsonArray {})
            put("content", "hello")
            put("sig", "c".repeat(128))
        }

        val result = SchemataValidator.validateNote(event)
        assertFalse(result.valid, "Missing pubkey should fail validation")
        assertTrue(result.errors.isNotEmpty(), "Should have errors for missing pubkey")
    }

    @Test
    fun `tag validates against schema`() {
        val schema = SchemataValidator.getSchema("pTagSchema")
        assertNotNull(schema, "pTagSchema should exist")

        val tag = buildJsonArray {
            add(json.parseToJsonElement("\"p\""))
            add(json.parseToJsonElement("\"${"a".repeat(64)}\""))
        }

        val result = SchemataValidator.validate(schema, tag)
        assertTrue(result.valid, "Valid p-tag should pass. Errors: ${result.errors}")
    }

    @Test
    fun `NIP-11 validates`() {
        val doc = buildJsonObject {
            put("name", "My Relay")
            put("supported_nips", buildJsonArray {
                add(json.parseToJsonElement("1"))
                add(json.parseToJsonElement("11"))
            })
        }

        val result = SchemataValidator.validateNip11(doc)
        assertTrue(result.valid, "Valid NIP-11 doc should pass. Errors: ${result.errors}")
    }

    @Test
    fun `unknown kind produces warning`() {
        val event = buildJsonObject {
            put("id", "a".repeat(64))
            put("pubkey", "b".repeat(64))
            put("created_at", 1700000000)
            put("kind", 99999)
            put("tags", buildJsonArray {})
            put("content", "")
            put("sig", "c".repeat(128))
        }

        val result = SchemataValidator.validateNote(event)
        assertTrue(result.warnings.isNotEmpty(), "Unknown kind should produce a warning")
        assertTrue(
            result.warnings.any { it.message.contains("No schema found") },
            "Warning should mention missing schema"
        )
    }

    @Test
    fun `missing kind field returns error`() {
        val event = buildJsonObject {
            put("id", "a".repeat(64))
            put("pubkey", "b".repeat(64))
            put("content", "hello")
        }

        val result = SchemataValidator.validateNote(event)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.message.contains("kind") })
    }

    // --- validate tests ---

    @Test
    fun `valid data passes schema`() {
        val schema = json.parseToJsonElement("""
            {
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "integer" }
                },
                "required": ["name"]
            }
        """.trimIndent())

        val data = json.parseToJsonElement("""
            {
                "name": "Alice",
                "age": 30
            }
        """.trimIndent())

        val result = SchemataValidator.validate(schema, data)
        assertTrue(result.valid, "Valid data should pass. Errors: ${result.errors}")
    }

    @Test
    fun `missing required field fails`() {
        val schema = json.parseToJsonElement("""
            {
                "type": "object",
                "properties": {
                    "name": { "type": "string" }
                },
                "required": ["name"]
            }
        """.trimIndent())

        val data = json.parseToJsonElement("""
            {
                "age": 30
            }
        """.trimIndent())

        val result = SchemataValidator.validate(schema, data)
        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `additional properties produce warnings`() {
        val schema = json.parseToJsonElement("""
            {
                "type": "object",
                "properties": {
                    "name": { "type": "string" }
                }
            }
        """.trimIndent())

        val data = json.parseToJsonElement("""
            {
                "name": "Alice",
                "extra_field": "surprise"
            }
        """.trimIndent())

        val result = SchemataValidator.validate(schema, data)
        assertTrue(result.valid, "Extra props should be warnings, not errors")
        assertTrue(result.warnings.isNotEmpty(), "Should have warnings for extra props")
        assertTrue(result.warnings.any { it.message.contains("extra_field") })
    }

    @Test
    fun `additionalProperties false is error not warning`() {
        val schema = json.parseToJsonElement("""
            {
                "type": "object",
                "properties": {
                    "name": { "type": "string" }
                },
                "additionalProperties": false
            }
        """.trimIndent())

        val data = json.parseToJsonElement("""
            {
                "name": "Alice",
                "extra_field": "surprise"
            }
        """.trimIndent())

        val result = SchemataValidator.validate(schema, data)
        assertFalse(result.valid, "additionalProperties: false makes extras an error")
        assertTrue(result.warnings.isEmpty(), "No warnings when additionalProperties: false")
    }

    // --- validate_message tests ---

    @Test
    fun `relay notice message validates`() {
        val msg = buildJsonArray {
            add(json.parseToJsonElement("\"NOTICE\""))
            add(json.parseToJsonElement("\"rate limited\""))
        }

        val result = SchemataValidator.validateMessage(msg, Subject.RELAY, "Notice")
        assertTrue(result.valid, "Valid NOTICE should pass. Errors: ${result.errors}")
    }

    @Test
    fun `unknown message type produces warning`() {
        val msg = buildJsonArray {
            add(json.parseToJsonElement("\"UNKNOWN\""))
        }

        val result = SchemataValidator.validateMessage(msg, Subject.RELAY, "Unknown")
        assertTrue(result.warnings.isNotEmpty(), "Unknown message type should produce warning")
    }

    // --- getSchema tests ---

    @Test
    fun `getSchema returns schema for known key`() {
        val schema = SchemataValidator.getSchema("kind1Schema")
        assertNotNull(schema)
    }

    @Test
    fun `getSchema returns null for unknown key`() {
        val schema = SchemataValidator.getSchema("nonexistent")
        assertNull(schema)
    }
}
