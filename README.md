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

## Release build

```sh
./gradlew :app:assembleRelease
```

Without a signing key this produces `app/build/outputs/apk/release/app-release-unsigned.apk` (minified, about 4.6 MB), which is what F-Droid builds and signs itself.

To sign your own release, keep the key outside the repository and put its four properties in `~/.gradle/gradle.properties`, never in this project:

```properties
cenitReleaseStoreFile=/Users/you/.android/cenit-release.jks
cenitReleaseStorePassword=…
cenitReleaseKeyAlias=cenit
cenitReleaseKeyPassword=…
```

Create the key once with `keytool -genkeypair -keystore ~/.android/cenit-release.jks -alias cenit -keyalg RSA -keysize 4096 -validity 36500` and back it up: losing it means published installs can no longer be updated.

To try a minified build on a phone that already has a debug build, without losing its data, sign it with the debug key: `./gradlew :app:assembleRelease -PcenitReleaseDebugSigning=true`.

## Third-party code

- [Astronomy Engine](https://github.com/cosinekitty/astronomy) v2.1.19 by Don Cross, MIT licence, vendored unmodified in [`third_party/astronomy/`](third_party/astronomy/) (provenance and integrity check in its README).

## Third-party data

- City search data: GeoNames (geonames.org), CC BY 4.0 ([`app/src/main/assets/geonames/cities.tsv`](app/src/main/assets/geonames/cities.tsv), 69,440 cities with population ≥ 5,000, downloaded 2026-11-20; regenerated with `python3 -I tools/geonames/build_cities.py`).
