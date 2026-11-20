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
```
