# Safe APK updates

## Goals

After installing a newer STREAM APK via Downloader, the user must:

- Keep Xtream login (no re-entry)
- Keep profiles, favourites, continue watching, history
- Keep filters / hidden categories / playback prefs
- Keep metadata already cached in Room where schema allows

## How STREAM enforces this

| Requirement | Implementation |
|-------------|----------------|
| Permanent `applicationId` | `tv.stream.app` (never change) |
| Same signing cert | `app/release.jks` — see [RELEASE_SIGNING.md](RELEASE_SIGNING.md) |
| Incrementing `versionCode` | Bumped every release in `app/build.gradle.kts` |
| Local persistence | Room (`strata-tv.db`) + DataStore + EncryptedSharedPreferences |
| Room migrations | Registered in `AppModule`; **no** `fallbackToDestructiveMigration()` |
| No startup wipe | `StrataApp` only migrates credentials; never clears DB/prefs |
| Backup / restore | Settings → Backup & restore → `stream-backup.json` |

## Upgrade test checklist

1. Install older APK (`adb install stream-old.apk`)
2. Log in, add favourites, start a movie (continue watching)
3. Export backup from Settings (optional safety)
4. Install newer APK: `adb install -r stream-new.apk` (no uninstall)
5. Confirm: still logged in, favourites present, continue watching intact
6. Confirm certificate: `apksigner verify --print-certs` matches prior APK

## Database policy

When changing Room entities:

1. Bump `AppDatabase.version`
2. Add `MIGRATION_n_n+1` in `Migrations.kt`
3. Register it in `AppModule.provideDatabase`
4. Commit schema JSON under `app/schemas/`

Never call `fallbackToDestructiveMigration()`. Prefer a failed migration
(visible crash / log) over silent data loss.

## Backup file locations

- App storage: `Android/data/tv.stream.app/files/backups/stream-backup.json`
- Public copy (when writable): `Download/stream-backup.json`

Backup includes credentials when exported from Settings (device-local only).
Catalogue content is re-synced from the provider after restore.
