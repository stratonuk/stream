# Test results

## Automated (host JVM)

| Suite | Result |
|-------|--------|
| `:app:testDebugUnitTest` | **69 tests, 0 failures, 0 errors** (2026-10-15) |
| ChannelCategorizerTest | Passed |
| ChannelDeduplicatorTest / TitleParserTest / TMDB / EPG matcher | Passed |

Run locally:

```bash
./gradlew :app:testDebugUnitTest
```

## Emulator

| Check | Result |
|-------|--------|
| Assemble release APK | **Passed** — `app/build/outputs/apk/release/app-release.apk` (4.0 MB) |
| Launch on Android TV emulator | Not yet run in this session |

## Physical Fire TV Stick

| Check | Result |
|-------|--------|
| Sideload via Downloader | **Not yet tested** — requires user device |
| Remote navigation | Not yet tested on device |
| Live / VOD playback with real credentials | Not yet tested on device |

Never claim physical-device pass until it has actually happened.
