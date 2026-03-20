plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
}

group = "nostrability"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("nostrability:schemata-kt")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // networknt json-schema-validator — most battle-tested JVM JSON Schema lib
    implementation("com.networknt:json-schema-validator:1.5.6")

    // Jackson for networknt (it requires Jackson internally)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
