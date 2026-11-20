import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Astronomy Engine for Kotlin (MIT), vendored unmodified from
// https://github.com/cosinekitty/astronomy tag v2.1.19. It is not published
// to Maven Central, and vendoring keeps the build on google() + mavenCentral().
// See README.md in this directory for provenance and how to update it.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Upstream code, compiled as published: its warnings are not ours to fix.
        suppressWarnings.set(true)
    }
}

// SHA-256 of the upstream files at commit 61dc07020aaa6885d2c7f688a4d82beaf6edb9ef (tag v2.1.19).
val vendoredFiles = mapOf(
    "src/main/kotlin/io/github/cosinekitty/astronomy/astronomy.kt" to
        "b698f7564fdbd6db78fa5777778c2538b377c1506470dfaa5669c57399c81c9a",
    "LICENSE" to "690dd98cb13ba4db77c6327deea852a816892bb9debbad5943405c66972f8023",
)

val verifyVendoredSource by tasks.registering {
    group = "verification"
    description = "Fails unless the vendored Astronomy Engine files are byte-identical to upstream v2.1.19."
    val files = vendoredFiles.mapKeys { (path, _) -> layout.projectDirectory.file(path).asFile }
    inputs.files(files.keys)
    doLast {
        files.forEach { (file, expected) ->
            val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(actual == expected) {
                "${file.relativeTo(rootDir)} differs from upstream Astronomy Engine v2.1.19 " +
                    "(sha256 $actual, expected $expected). Vendored files must stay unmodified."
            }
        }
    }
}

tasks.named("compileKotlin") { dependsOn(verifyVendoredSource) }
