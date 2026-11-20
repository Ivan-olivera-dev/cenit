# Cenit

Open-source sun and moon planner for photographers, for Android. Status: **in development**.

- Sunrise, sunset, golden and blue hour, twilights, moonrise, moonset and moon phase for any date and place.
- A flat compass of the sky: the sun's path hour by hour, the solstices and where to point.
- Works offline: Cenit has no internet permission. Location comes from the phone's GPS, manual coordinates or a bundled city list.
- No ads, no tracking. MIT licence.

Design: [`design/system.md`](design/system.md) and the artboards under [`design/`](design/).

## Build

Requires the Android SDK. Gradle provisions JDK 21 itself (`gradle/gradle-daemon-jvm.properties`).

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk
scripts/test-check-manifest.sh   # tests of the manifest guard itself
```

Trust points of the build: the Gradle distribution is pinned by `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties`. The JDK 21 that runs the Gradle daemon is downloaded over HTTPS from the foojay redirect URLs in `gradle/gradle-daemon-jvm.properties`, which carry no checksum; to avoid that download, install a JDK 21 yourself (Gradle uses a matching local JDK first). Neither affects the shipped APK.

## Third-party code

- [Astronomy Engine](https://github.com/cosinekitty/astronomy) v2.1.19 by Don Cross, MIT licence, vendored unmodified in [`third_party/astronomy/`](third_party/astronomy/) (provenance and integrity check in its README).
