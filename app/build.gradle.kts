import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.ivan_olivera_dev.cenit"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.ivan_olivera_dev.cenit"
        minSdk = 29
        targetSdk = 35
        // CI stamps these from the tag: -PversionName=1.2.3 -PversionCode=42
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1.0"
    }

    // Release signing comes only from the developer's own ~/.gradle/gradle.properties (never this
    // repository): cenitReleaseStoreFile, cenitReleaseStorePassword, cenitReleaseKeyAlias,
    // cenitReleaseKeyPassword. Without all four the release APK is unsigned, as F-Droid expects.
    val releaseSigning = listOf(
        "cenitReleaseStoreFile", "cenitReleaseStorePassword", "cenitReleaseKeyAlias", "cenitReleaseKeyPassword",
    ).associateWith { findProperty(it) as String? }
    if (releaseSigning.values.all { !it.isNullOrBlank() }) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseSigning.getValue("cenitReleaseStoreFile")!!)
                storePassword = releaseSigning.getValue("cenitReleaseStorePassword")
                keyAlias = releaseSigning.getValue("cenitReleaseKeyAlias")
                keyPassword = releaseSigning.getValue("cenitReleaseKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            // -PcenitReleaseDebugSigning=true signs a local release with the debug key, so a minified
            // build can replace an installed debug build for testing without losing its data.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug").takeIf { findProperty("cenitReleaseDebugSigning") == "true" }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // No Google-encrypted dependency blob in the APK/AAB: nothing opaque ships with Cenit.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Reproducible builds: strip non-deterministic timestamps and enforce stable entry ordering.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    testImplementation(libs.junit)
}

// BundledCitiesAssetTest reads the bundled city list by path; declare it as an
// input so a regenerated list re-runs the unit tests instead of reusing old results.
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/assets/geonames").withPropertyName("geonamesAsset")
}
