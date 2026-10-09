# relay-client-android

**Not the Android test APK.** Visual parity and JNI gaps belong in sibling **`relay-runtime`**: [ANDROID_WEB_TEST.md](../relay-runtime/docs/ANDROID_WEB_TEST.md) (glossary: [TERMINOLOGY.md](../relay-runtime/docs/TERMINOLOGY.md)). This tree is the full **RelayClient** product shell. GitLab: `clevertree/react-client-android`.

Native Relay client using **relay-runtime JNI** (no JSC, no hook-transpiler). Copy `librelay_runtime.so` after `relay-runtime/scripts/build-android.sh`. In-tree demo APK: `relay-runtime/android-demo`.

Two product flavors:

| Flavor | Id | Chrome |
|--------|----|--------|
| `relay` | `com.relay.pure` | Server list, repo tabs, shell settings. Default origin is Flashback Repository and can be changed. |
| `flashback` | `com.flashback.repository` | `RELAY_LOCK_ORIGIN`. Opens `https://flashbackrepository.org` only. Does not create the server list, tabs, or shell settings. The live site’s own UI, including its settings, is the app. |

```bash
cd android && ./gradlew :app:assembleRelayDebug :app:assembleFlashbackDebug

# Published Flashback download (release-signed; needs android/keystore.properties):
#   ./gradlew :app:assembleFlashbackRelease
# Keystore: ~/.android/flashback-release.keystore (never commit). See fan-edits downloads/README.md.
```

`navigate()` and CleverScript `localStorage` persistence are in `relay-runtime` and apply after the `.so` is rebuilt. Relative links and the hook reload work with the current library.

## fan-edits library UI (after runtime demo ≈ web)

1. Start fan-edits gateway on the dev machine:

   ```bash
   cd ~/dev/relay/relay-client-web && npm run serve:fan-edits
   ```

2. Build APK:

   ```bash
   cd android && ./gradlew :app:assembleDebug
   ```

3. Device / emulator:

   ```bash
   adb reverse tcp:3011 tcp:3011   # USB device → host localhost:3011
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.relay.pure/com.relay.client.MainActivity
   ```

   Emulator without `adb reverse`: use `RELAY_REMOTE_HOST=http://10.0.2.2:3011`.

## Components

| File | Role |
|------|------|
| `HookRenderer.kt` | Loads remote/local hooks, stable render loop |
| `VNodeRenderer.kt` | VNode JSON → Android Views |
| `RelayRuntimeHostHttp.java` | Sync HTTP for CleverScript `fetch()` |
| `RelayRuntime.kt` + `jniLibs/` | JNI bridge (copy from `relay-runtime/android` after `scripts/build-android.sh`) |

Reference demo: `relay-runtime/android-demo`.

Legacy QuickJS/Hermes docs are in [`docs/archive/`](docs/archive/).
