plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM on purpose: the ephemeris has no Android APIs, so it is
// testable without an emulator and portable to Kotlin Multiplatform later.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":astronomy"))
    testImplementation(libs.junit)
}
