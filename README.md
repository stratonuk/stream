# STREAM

Premium native IPTV client for **Amazon Fire TV** and **Android TV**.

Working name: **STREAM**. Monochrome Apple TV / Netflix-inspired UI for legitimate Xtream-compatible subscriptions.

## Install on Fire Stick (Downloader)

1. Install **Downloader** from the Amazon Appstore.
2. Open Downloader and enter the latest APK URL from the [Releases](../../releases) page:

```
https://github.com/stratonuk/stream/releases/latest/download/stream.apk
```

3. Install when prompted, then launch **STREAM** from your apps row.
4. Enter your Xtream server URL, username and password.

## What you get

- Jetpack Compose for TV, D-pad first navigation
- Xtream Codes login + catalogue sync (live / movies / series / EPG)
- Media3 ExoPlayer with custom monochrome player controls
- UK-first live TV category normalisation (BBC, ITV, Sky, Sports, …)
- TMDB metadata enrichment (optional API key)
- Continue Watching, My List, local profiles, search
- Offline-friendly local Room database — no app backend required

## Build

Requirements: **JDK 17**, Android SDK API 34.

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=~/Library/Android/sdk

# Optional TMDB key (local.properties is gitignored)
echo "tmdb.api.key=YOUR_KEY" >> local.properties

./gradlew :app:assembleRelease
```

APK output:

```
app/build/outputs/apk/release/app-release.apk
```

Debug APK:

```bash
./gradlew :app:assembleDebug
```

## Safe updates (Downloader)

Future APKs keep the same `applicationId` (`tv.stream.app`) and the same
release signing certificate so Fire Stick installs **overwrite in place**
without losing login, favourites or continue watching.

| Doc | Purpose |
|-----|---------|
| [SAFE_UPDATES.md](SAFE_UPDATES.md) | In-place update rules + checklist |
| [RELEASE_SIGNING.md](RELEASE_SIGNING.md) | Permanent keystore process |

## Documentation

| Doc | Purpose |
|-----|---------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layers, modules, data flow |
| [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) | Toolchain & Gradle |
| [FIRE_TV_INSTALLATION.md](FIRE_TV_INSTALLATION.md) | ADB + Downloader |
| [FEATURE_STATUS.md](FEATURE_STATUS.md) | What ships / what is pending |
| [TEST_RESULTS.md](TEST_RESULTS.md) | Automated vs device tests |
| [docs/design/design-reference.jpg](docs/design/design-reference.jpg) | Approved UI direction |

## Licence & attribution

STREAM is based on [Strata-IPTV](https://github.com/Yokomoko/Strata-IPTV) (MIT). See [LICENSE](LICENSE) and [NOTICE](NOTICE).

STREAM is a **client only**. It does not host or sell content. You must use an authorised provider subscription that is legal in your jurisdiction.

## Repository audit (Phase 0)

| Candidate | Licence | Stack | Verdict |
|-----------|---------|-------|---------|
| [Yokomoko/Strata-IPTV](https://github.com/Yokomoko/Strata-IPTV) | MIT | Kotlin, Compose TV, Media3, Xtream, TMDB, Room | **Adopted as foundation** |
| [ahXN00/OwnTV](https://github.com/ahXN00/OwnTV) / fork | GPL-3.0 | Kotlin Compose TV | Rejected (viral licence) |
| [opentvproject/opentv](https://github.com/opentvproject/opentv) | GPL-3.0 | Kotlin Compose TV | Rejected (viral licence) |

Strata already implemented the preferred stack. STREAM rebrands, applies the approved monochrome design system, and tightens UK-first live category intelligence.
