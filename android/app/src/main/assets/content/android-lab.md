# Android APK lab

Fast checks for Flashback Repository installs (`com.flashback.repository`). Use this page on a computer while the phone runs the app, or open **Lab** in the app library.

Download: **[flashback.apk](/downloads/flashback.apk)** — sideload steps on [Setup](/setup).

## What “pass” looks like

| Check | Pass | Fail → do this |
| --- | --- | --- |
| Install + open | Library loads titles from `/index.json` | Network / origin; retry |
| Ownership | Title → **OK — I confirm** (one button, no checkbox) | Report UI regression |
| HEVC probe | **Lab** says “HEVC hardware decode: yes” | Expect in-app fail; use external |
| In-app play | Picture after confirm on a `showing` title | Overlay: **Open in external player** |
| Error overlay | **Lab → Simulate decode error** shows full card (title + body + two buttons), **not** a truncated system toast | Rebuild APK if you still see a white toast bubble |
| External handoff | Chooser → VLC plays the HTTPS `.mkv` | Install [VLC](https://play.google.com/store/apps/details?id=org.videolan.vlc) |

## In-app Lab

1. Open the Flashback app → **Lab** (next to Setup / Legal).
2. Read the HEVC line (MediaCodec hardware probe).
3. Tap **Simulate decode error** to verify the error card copy and buttons without needing a broken device.
4. Tap **Try sample stream** on a catalogued master (needs network).
5. Tap **Open sample externally** to confirm the chooser.

## Sample streams (HTTPS masters)

After ownership on a title, the app streams progressive Matroska (HTTP Range). Examples (change if catalog moves):

- [The Mummy (1999) — Debugged](/the-mummy-1999-debugged)
- [The Mummy Returns (2001) — Debugged](/the-mummy-returns-2001-debugged)
- [Van Helsing — DeAnna'd](/van-helsing-deanna-d)

Masters are HEVC in `.mkv`. Soft FFmpeg decode is **not** in the APK.

### Quick playback path (APK)

1. Library → open **The Mummy (1999) — Debugged** (or another `showing` title).
2. Tap **OK — I confirm** (one button; hit counter fires like the web).
3. App rotates to landscape immersive theater and binds the encoded HTTPS `.mkv` in ExoPlayer.
4. If the picture never appears: **Lab** → check HEVC hardware; use **External** / error-card **Open in external player**.
## Error copy contract

When decode fails, the overlay must show:

1. **Title** — short (`Can't play in this app`, …)
2. **Body** — full sentences (HEVC / network / format)
3. **Open in external player**
4. **Get VLC**

Unit tests live in `relay-client-android` (`PlaybackErrorsTest`, `HevcCapabilityTest`). See [ANDROID_APK.md](/docs/ANDROID_APK.md) and [ANDROID_PLAYER_BACKLOG.md](/docs/ANDROID_PLAYER_BACKLOG.md).

## Build / install (maintainers)

```bash
cd /path/to/relay-client-android/android
./gradlew :app:testFlashbackDebugUnitTest :app:assembleFlashbackDebug
adb install -r app/build/outputs/apk/flashback/debug/app-flashback-debug.apk
# optional publish path:
cp app/build/outputs/apk/flashback/debug/app-flashback-debug.apk \
  /path/to/fan-edits/downloads/flashback.apk
```
