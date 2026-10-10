# Flashback embedded Kubo

Ships `libkubo_daemon.so` (Kubo `cmd/ipfs`) for **arm64-v8a** and **armeabi-v7a**.
The APK execs the binary from `nativeLibraryDir` and talks HTTP to a localhost API/gateway.

```bash
./build-kubo-android.sh
# then from android/:
./gradlew :app:syncIpfsKubo :app:assembleFlashbackDebug
```

Prebuilt binaries are local (large); regenerate with the script after NDK/Go upgrades.

## Spike results (2026-10-10)

| ABI | Binary | Packaged | Runtime |
|-----|--------|----------|---------|
| arm64-v8a | 75 MiB `libkubo_daemon.so` | release split ✓ | phone pending when device available |
| armeabi-v7a | 69 MiB | release split ✓ | Fire TV install pending (host unreachable during spike) |
| x86_64 | 77 MiB | debug/emulator | `IpfsNodeSmokeTest` **passed** (daemon extract + API ready) |

Go 1.23 + NDK 27; link with `-ldflags=-checklinkname=0`. App extracts/execs daemon from APK when `nativeLibraryDir` is not executable.
