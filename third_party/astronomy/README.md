# Astronomy Engine (vendored)

Unmodified copy of the Kotlin/JVM edition of [Astronomy Engine](https://github.com/cosinekitty/astronomy) by Don Cross, MIT licence ([`LICENSE`](LICENSE)). Cenit's `:core` uses it for every sun and moon calculation.

| | |
| --- | --- |
| Upstream | `https://github.com/cosinekitty/astronomy` |
| Tag | `v2.1.19` (commit `61dc07020aaa6885d2c7f688a4d82beaf6edb9ef`) |
| Files | `source/kotlin/src/main/kotlin/io/github/cosinekitty/astronomy/astronomy.kt` → `src/main/kotlin/io/github/cosinekitty/astronomy/astronomy.kt`; `LICENSE` → `LICENSE` |

Why vendored: the library is published only on JitPack, not Maven Central. Copying the single source file keeps Cenit's build on `google()` and `mavenCentral()`, lets it build offline and keeps it buildable from source (F-Droid).

Integrity: `./gradlew :astronomy:verifyVendoredSource` (also run before every compilation of this module) fails unless both files have the SHA-256 recorded in [`build.gradle.kts`](build.gradle.kts), which are the hashes of the upstream files at the commit above. Never edit these files; Cenit code lives in `:core`.

To update: download both files from the new tag, replace them, update the tag, commit and hashes here and in `build.gradle.kts`, and run `./gradlew :core:test`.
