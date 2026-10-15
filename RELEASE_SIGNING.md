# Release signing (safe Fire Stick updates)

STREAM APKs are installed via Amazon Fire TV **Downloader**. Android only
allows an in-place update when **all** of these match:

1. Same `applicationId` → permanently `tv.stream.app`
2. Same signing certificate for every release
3. Higher `versionCode` than the installed build

If any of those break, the user must uninstall (and lose local data).

## Permanent certificate

| Item | Value |
|------|--------|
| Keystore (local, gitignored) | `app/release.jks` |
| Offline backup | `~/.stream-signing/stream-release.jks` |
| Offline credentials | `~/.stream-signing/credentials.env` |
| Alias | `stream` |
| Certificate SHA-256 | `CB:BF:8D:3B:51:A2:4F:62:03:9C:1A:29:A8:A6:B2:72:BC:F9:3E:CE:07:7E:66:8F:09:5F:83:A2:9B:1E:26:8B` |

This certificate matches the one used for **v1.0.0**, so users who already
installed that APK can update to newer releases without uninstalling.

**Never commit** `app/release.jks`, `*.jks`, or passwords.

## Local setup (`local.properties` — gitignored)

```properties
sdk.dir=/path/to/Android/sdk
release.keystore.password=YOUR_PASSWORD
release.key.alias=stream
release.key.password=YOUR_PASSWORD
```

Or export env vars:

```bash
source ~/.stream-signing/credentials.env
export RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD
# Place keystore at app/release.jks (copy from ~/.stream-signing/)
cp "$HOME/.stream-signing/stream-release.jks" app/release.jks
```

## Build a release APK

```bash
./gradlew :app:assembleRelease
```

Release builds **fail** if the permanent keystore is missing. Do not ship
debug-signed APKs for Downloader.

Verify the APK certificate before publishing:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
# SHA-256 must match the fingerprint above.
```

## Versioning every release

In `app/build.gradle.kts`:

- Increment `versionCode` by at least 1 (required)
- Bump `versionName` (e.g. `1.0.1` → `1.0.2`)

## GitHub Actions secrets

Set these repository secrets for CI releases:

| Secret | Purpose |
|--------|---------|
| `RELEASE_KEYSTORE_BASE64` | `base64 -i app/release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | `stream` |
| `RELEASE_KEY_PASSWORD` | key password |
| `TMDB_API_KEY` | optional enrichment |

## If you lose the keystore

You cannot update existing installs. Users would need to uninstall and
reinstall (data loss unless they used **Settings → Backup & restore**).
Back up `~/.stream-signing/` immediately and keep it offline.
