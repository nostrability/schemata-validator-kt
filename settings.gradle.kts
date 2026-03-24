rootProject.name = "schemata-validator-kt"

// Use composite build for local dev; on JitPack, schemata-kt comes from JitPack itself.
if (System.getenv("JITPACK") == null) {
    includeBuild("../schemata-kt")
}
