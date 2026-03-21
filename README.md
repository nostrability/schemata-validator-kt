# schemata-validator-kt

[![Test](https://github.com/nostrability/schemata-validator-kt/actions/workflows/test.yml/badge.svg)](https://github.com/nostrability/schemata-validator-kt/actions/workflows/test.yml)

Kotlin/JVM validator for [Nostr](https://nostr.com/) protocol JSON schemas. This is the Kotlin equivalent of [`schemata-validator-rs`](https://github.com/nostrability/schemata-validator-rs), built on top of [`schemata-kt`](https://github.com/nostrability/schemata-kt).

Validates Nostr events, NIP-11 documents, and protocol messages against the canonical [schemata](https://github.com/nostrability/schemata) definitions using Draft-07 JSON Schema via [networknt/json-schema-validator](https://github.com/networknt/json-schema-validator).

## Related projects

| Project | Language | Role |
|---------|----------|------|
| [nostrability/schemata](https://github.com/nostrability/schemata) | JSON/JS | Canonical schema definitions |
| [schemata-kt](https://github.com/nostrability/schemata-kt) | Kotlin | Data package (schemas + registry) |
| [schemata-rs](https://github.com/nostrability/schemata-rs) | Rust | Data crate (schemas + registry) |
| [schemata-validator-rs](https://github.com/nostrability/schemata-validator-rs) | Rust | Rust validator |

## When to use this

JSON Schema validation is [not suited for runtime hot paths](https://github.com/nostrability/schemata#what-is-it-not-good-for). Use this in **CI and integration tests** to catch schema drift at build time.

Good for:
- CI pipelines that verify your event construction matches the canonical schemas
- Integration tests for Kotlin/JVM clients and relays
- Fuzz testing to discover broken event shapes

Not good for:
- Validating every incoming event at runtime
- Hot paths where latency matters

## Usage

Add as a **test dependency**:

```kotlin
// settings.gradle.kts
includeBuild("../schemata-kt")
includeBuild("../schemata-validator-kt")
```

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("nostrability:schemata-validator-kt")
}
```

### Example: validate event construction in tests

```kotlin
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nostrability.schemata.validator.SchemataValidator
import nostrability.schemata.validator.Subject
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class MyEventTest {

    @Test
    fun `my event matches schema`() {
        val event = buildJsonObject {
            put("id", "a".repeat(64))
            put("pubkey", "b".repeat(64))
            put("created_at", 1700000000)
            put("kind", 1)
            put("tags", buildJsonArray {})
            put("content", "hello world")
            put("sig", "c".repeat(128))
        }

        val result = SchemataValidator.validateNote(event)
        assertTrue(result.valid, "errors: ${result.errors}")
    }

    @Test
    fun `my nip11 doc matches schema`() {
        val doc = buildJsonObject {
            put("name", "My Relay")
            put("supported_nips", buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(1))
                add(kotlinx.serialization.json.JsonPrimitive(11))
            })
        }

        val result = SchemataValidator.validateNip11(doc)
        assertTrue(result.valid)
    }

    @Test
    fun `relay message matches schema`() {
        val msg = buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("NOTICE"))
            add(kotlinx.serialization.json.JsonPrimitive("rate limited"))
        }

        val result = SchemataValidator.validateMessage(msg, Subject.RELAY, "Notice")
        assertTrue(result.valid)
    }

    @Test
    fun `tag matches schema`() {
        val schema = SchemataValidator.getSchema("pTagSchema")!!
        val tag = buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("p"))
            add(kotlinx.serialization.json.JsonPrimitive("a".repeat(64)))
        }
        val result = SchemataValidator.validate(schema, tag)
        assertTrue(result.valid)
    }
}
```

### CI workflow

```yaml
# .github/workflows/schema-validation.yml
name: Schema Validation
on: [push, pull_request]
jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - run: ./gradlew test
```

## API

| Function | Description |
|----------|-------------|
| `SchemataValidator.validate(schema, data)` | Validate data against any JSON schema |
| `SchemataValidator.validateNote(event)` | Validate a Nostr event (looks up `kind{N}Schema`) |
| `SchemataValidator.validateNip11(doc)` | Validate a NIP-11 relay info document |
| `SchemataValidator.validateMessage(msg, subject, slug)` | Validate a relay/client protocol message |
| `SchemataValidator.getSchema(key)` | Look up a schema from the registry |

### ValidationResult

```kotlin
data class ValidationResult(
    val valid: Boolean,           // true if no errors
    val errors: List<ValidationError>,  // schema violations
    val warnings: List<ValidationError> // additional properties, missing schemas
)
```

Errors are hard schema violations. Warnings flag additional properties not in the schema and unknown kinds/message types.

## Building

```bash
# First vendor schemas in schemata-kt
cd ../schemata-kt && make vendor

# Then build the validator
cd ../schemata-validator-kt && ./gradlew build
```

Requires JDK 17+.

## License

GPL-3.0-or-later
