pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Provisions the JDK 21 pinned in gradle/gradle-daemon-jvm.properties;
    // Kotlin 2.0.x cannot run on newer JDKs such as Android Studio's bundled 25.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "cenit"
include(":app", ":core")
