plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    `maven-publish`
}

group = "nostrability"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val onJitPack = System.getenv("JITPACK") != null

dependencies {
    // Local dev: composite build resolves "nostrability:schemata-kt"
    // JitPack: no composite build, so use the JitPack coordinate
    if (onJitPack) {
        implementation("com.github.nostrability:schemata-kt:v0.1.1")
    } else {
        implementation("nostrability:schemata-kt")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // networknt json-schema-validator — most battle-tested JVM JSON Schema lib
    implementation("com.networknt:json-schema-validator:1.5.6")

    // Jackson for networknt (it requires Jackson internally)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
