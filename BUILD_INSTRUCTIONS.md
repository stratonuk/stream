# Build instructions

## Prerequisites

- macOS / Linux / Windows
- **JDK 17** (Temurin recommended)
- Android SDK with:
  - `platforms;android-34`
  - `build-tools;34.0.0`
  - `platform-tools`

## Configure

Create `local.properties` (gitignored):

```properties
sdk.dir=/Users/YOU/Library/Android/sdk
tmdb.api.key=OPTIONAL_TMDB_KEY
```

## Build APKs

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Outputs:

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

Release signing: if `app/release.jks` + passwords are not configured, Gradle signs with the debug keystore so the APK remains installable for personal testing.

## Unit tests

```bash
./gradlew :app:testDebugUnitTest
```

## Notes

- Min SDK 21 (covers current Fire Stick lineup)
- ABI filters: `armeabi-v7a`, `arm64-v8a`
- Cleartext HTTP allowed for providers that still use HTTP (HTTPS preferred; wizard warns)
