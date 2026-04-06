# schemata-validator-kt

[![Test](https://github.com/nostrability/schemata-validator-kt/actions/workflows/test.yml/badge.svg)](https://github.com/nostrability/schemata-validator-kt/actions/workflows/test.yml)
[![JitPack](https://jitpack.io/v/nostrability/schemata-validator-kt.svg)](https://jitpack.io/#nostrability/schemata-validator-kt)
[![License](https://img.shields.io/badge/license-GPL--3.0--or--later-blue?style=flat-square)](LICENSE)

Kotlin/JVM validator for [Nostr](https://nostr.com/) protocol JSON schemas. Built on [`schemata-kt`](https://github.com/nostrability/schemata-kt) and [networknt json-schema-validator](https://github.com/networknt/json-schema-validator) (Draft 7).

## Overview

`schemata-validator-kt` wraps the `schemata-kt` embedded JSON Schema definitions with networknt validation, exposing ready-to-use validation functions for common Nostr data structures. It validates Nostr events by kind, NIP-11 relay information documents, and relay/client protocol messages.

Validation results include both hard errors (schema violations) and soft warnings (additional properties not defined in the schema).

## When to use this

JSON Schema validation is [not suited for runtime hot paths](https://github.com/nostrability/schemata#what-is-it-not-good-for). Use this in:

- **CI pipelines** catching schema drift during builds
- **Integration tests** for clients and relays
- **Fuzz testing** to identify malformed event structures

## Installation

**Via JitPack** (recommended):

```kotlin
// build.gradle.kts
repositories {
    maven("https://jitpack.io")
}

dependencies {
    testImplementation("com.github.nostrability:schemata-validator-kt:v0.1.0")
}
```

**Local development** (composite build):

```kotlin
// settings.gradle.kts
includeBuild("../schemata-kt")
includeBuild("../schemata-validator-kt")
```

Requires JDK 17+. First run `make vendor` in `schemata-kt`, then `./gradlew build` in this project.

## Quick Start

```kotlin
import nostrability.schemata.validator.SchemataValidator
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

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
assert(result.valid) { "Errors: ${result.errors}" }
// result.errors is empty, result.warnings may flag additional properties
```

## API

All methods are on the `SchemataValidator` singleton object.

### `SchemataValidator.validate(schema, data)`

```kotlin
fun validate(schema: JsonElement, data: JsonElement): ValidationResult
```

Low-level validator. Compiles a JSON Schema with networknt (Draft 7) and validates `data` against it. Strips nested `$id` fields from the schema to prevent resolution issues. Enriches error messages from custom `errorMessage` fields in schemas. Use `validateNote`, `validateNip11`, or `validateMessage` for common cases.

| Parameter | Type | Description |
|-----------|------|-------------|
| `schema` | `JsonElement` | A JSON Schema document (kotlinx.serialization) |
| `data` | `JsonElement` | The data to validate |

### `SchemataValidator.validateNote(event)`

```kotlin
fun validateNote(event: JsonElement): ValidationResult
```

Validates a Nostr event against the schema for its `kind`. The schema is looked up from `schemata-kt` using the key `kind{N}Schema`. Returns a warning (not an error) if no schema exists for the given kind.

| Parameter | Type | Description |
|-----------|------|-------------|
| `event` | `JsonElement` | A Nostr event object |

### `SchemataValidator.validateNip11(doc)`

```kotlin
fun validateNip11(doc: JsonElement): ValidationResult
```

Validates a NIP-11 relay information document — the metadata object a relay serves at its HTTP endpoint — against the `nip11Schema`.

| Parameter | Type | Description |
|-----------|------|-------------|
| `doc` | `JsonElement` | A NIP-11 relay info document |

### `SchemataValidator.validateMessage(msg, subject, slug)`

```kotlin
fun validateMessage(msg: JsonElement, subject: Subject, slug: String): ValidationResult
```

Validates a Nostr protocol message against the schema for the given subject and message type. The schema key is constructed as `{subject}{Slug}Schema` (e.g., `relayNoticeSchema` for `subject=RELAY`, `slug="notice"`).

| Parameter | Type | Description |
|-----------|------|-------------|
| `msg` | `JsonElement` | The protocol message to validate |
| `subject` | `Subject` | Message origin: `Subject.RELAY` or `Subject.CLIENT` |
| `slug` | `String` | Message type name (e.g., `"notice"`, `"event"`, `"ok"`) |

### `SchemataValidator.getSchema(key)`

```kotlin
fun getSchema(key: String): JsonElement?
```

Looks up a schema by key from the `schemata-kt` registry. Returns `null` if the key doesn't exist.

| Parameter | Type | Description |
|-----------|------|-------------|
| `key` | `String` | Schema registry key (e.g., `"kind1Schema"`, `"pTagSchema"`) |

### `ValidationResult`

```kotlin
data class ValidationResult(
    val valid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<ValidationError>,
)
```

- `valid` — `true` if the data passes all schema constraints
- `errors` — schema violations; empty when `valid` is `true`
- `warnings` — additional property alerts; populated even when `valid` is `true`

### `ValidationError`

```kotlin
data class ValidationError(
    val instancePath: String,
    val keyword: String,
    val message: String,
    val schemaPath: String,
)
```

### `Subject`

```kotlin
enum class Subject {
    RELAY,
    CLIENT;
}
```

## Usage Examples

**Event validation:**

```kotlin
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
assert(result.valid)
```

**NIP-11 validation:**

```kotlin
val doc = buildJsonObject {
    put("name", "My Relay")
    put("supported_nips", buildJsonArray {
        add(Json.parseToJsonElement("1"))
        add(Json.parseToJsonElement("11"))
    })
}
val result = SchemataValidator.validateNip11(doc)
assert(result.valid)
```

**Protocol message validation:**

```kotlin
val msg = Json.parseToJsonElement("""["NOTICE", "rate limited"]""")
val result = SchemataValidator.validateMessage(msg, Subject.RELAY, "notice")
assert(result.valid)
```

**Direct schema lookup:**

```kotlin
val schema = SchemataValidator.getSchema("pTagSchema")!!
val tag = buildJsonArray {
    add(Json.parseToJsonElement("\"p\""))
    add(Json.parseToJsonElement("\"${"a".repeat(64)}\""))
}
val result = SchemataValidator.validate(schema, tag)
assert(result.valid)
```

## Known Limitations

- **Partial kind coverage:** Only event kinds with a corresponding schema in `@nostrability/schemata` can be validated. `validateNote` returns a warning (not an error) when no schema exists for the given kind.
- **No recursive content validation:** The `content` field of events containing stringified JSON (e.g., kind 0 metadata) is not recursively validated.
- **Alpha accuracy:** False positives and negatives are possible. The underlying schemas are in active development.

## Related Packages

- [`schemata-kt`](https://github.com/nostrability/schemata-kt) — Kotlin data package containing embedded schemas and registry
- [`@nostrability/schemata`](https://github.com/nostrability/schemata) — canonical language-agnostic schema definitions
- [`@nostrwatch/schemata-js-ajv`](https://github.com/sandwichfarm/nostr-watch/tree/next/libraries/schemata-js-ajv) — JavaScript/TypeScript validator implementation

## License

[GPL-3.0-or-later](LICENSE)
