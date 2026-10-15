# Fire TV installation

## Option A — Downloader (recommended)

1. On the Fire Stick, install **Downloader** from the Amazon Appstore.
2. Open Downloader → enter the release APK URL:

```
https://github.com/stratonuk/stream/releases/latest/download/stream.apk
```

3. Allow install from unknown sources when prompted.
4. Launch **STREAM**.

## Option B — ADB

Enable **ADB debugging** and **Apps from Unknown Sources** on the Fire Stick
(Settings → My Fire TV → Developer Options).

```bash
# Discover device IP in Network settings, then:
adb connect FIRE_TV_IP:5555
adb devices

# Install / update
adb install -r app/build/outputs/apk/release/app-release.apk

# Launch
adb shell am start -n tv.stream.app/.MainActivity

# Logs
adb logcat | grep -i stream

# Uninstall
adb uninstall tv.stream.app
```

ADB debugging is **not** enabled by default — you must turn it on once.
