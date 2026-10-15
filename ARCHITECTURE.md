# Architecture

## Stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| UI | Jetpack Compose for TV |
| Architecture | MVVM + repository / domain services |
| DI | Hilt |
| Playback | AndroidX Media3 ExoPlayer (+ HLS) |
| Networking | OkHttp / Retrofit-style Xtream JSON client |
| Local DB | Room |
| Preferences | DataStore |
| Images | Coil |
| Async | Coroutines + Flow |
| Background | WorkManager |

## Package layout (`com.strata.tv`)

Internal Kotlin package retains the Strata namespace for merge safety.
Application ID for installable APK: `tv.stream.app`.

```
ui/          Compose screens (home, live, movies, shows, player, search, settings, mylist, profiles)
domain/      Pure JVM logic (categoriser, dedupe, title parse, genre grouper)
data/xtream  Xtream Codes player_api + M3U fallback
data/tmdb    Metadata enrichment
data/db      Room entities / DAOs
data/repo    Sync, EPG, bootstrap
di/          Hilt modules
```

## Data flow

1. Setup wizard stores Xtream host / user / password in encrypted preferences (DataStore + Android Keystore where configured).
2. `SyncService` pulls live / VOD / series catalogues into Room.
3. `ChannelCategorizer` maps raw provider groups into local UK-first display buckets (multi-tag).
4. UI observes Room Flows; TMDB enrichment fills posters/plots progressively via WorkManager.
5. Player builds stream URLs from provider credentials + content IDs; progress written to continue-watching.

## Design system

Monochrome tokens in `ui/theme/StrataColors.kt` and `res/values/colors.xml`.
Approved reference: `docs/design/design-reference.jpg`.
No purple / blue / neon chrome accents.
