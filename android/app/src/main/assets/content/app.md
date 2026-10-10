# Flashback App

The **Flashback Repository** Android app plays our full-quality HEVC Matroska streams in-app (with soft subtitle tracks). It is **sideloaded on purpose** — there is no realistic Play Store path for this catalog ([why](/motivation#no-play-store-path)).

Computer browsers still use [Setup / VLC](/setup). This page is for **phones, tablets, and Android / Fire TV**.

Share this page (`/app`) — not a raw `.apk` URL — so people always get the current version and the right install steps.

---

## Install on phone or tablet

1. On the website, use the **versioned download buttons** at the top of this page (universal build — fine for most phones).
2. If Android asks whether to allow installs from the browser or Files app, tap **Allow** / open **Settings** and turn on install permission for that app, then go back and open the download.
3. Open the downloaded **flashback.apk** → **Install** → **Open**.
4. Pick a title → tap **OK — I confirm** (ownership affirmation).
5. Tap **Play**. The picture may rotate to landscape. Playback pulls the feature over **IPFS** on the device (local Kubo); use **Keep offline copy** on the title page to pin it.

You do **not** need a computer or Terminal for Android. Already installed? Use **Check for updates** in the app menu, or re-open this page on the site.

---

## Fire TV / Android TV

1. Prefer the **32-bit (Fire TV)** download on Amazon Fire TV and many older sticks (“App not installed” / library / ABI errors mean the universal or arm64 build does not match the device).
2. On Fire TV: enable **Apps from Unknown Sources** (and **ADB** if you install over the network from a computer).
3. Transfer the APK (USB, cloud download on the TV browser, or `adb install`), then open it and install.
4. Launch **Flashback Repository** from your apps row.

If in-app Play fails with a codec / decoder error on a 1080p-only stick while the master is 4K HEVC, pick **1080p (H.264)** on the title page (when catalogued), or **Open in external player** → [VLC](https://play.google.com/store/apps/details?id=org.videolan.vlc).

---

## Common problems

| What you see | What to do |
| --- | --- |
| “App not installed” / no matching ABIs | Use the **32-bit (armeabi-v7a)** APK above (Fire TV / older Android TV). |
| “There was a problem parsing the package” | The file is probably HTML/JSON saved as `.apk` (a few KB). Re-download from this page; confirm it is several MB. Or copy the APK from a computer via USB. |
| “Unsafe app blocked” / Play Protect | Normal for sideload. **More details** → **Install anyway**. Or briefly allow installs in Play Protect settings, install, then turn scanning back on. |
| Update will not install over an old build | Uninstall the old Flashback app first (signing key changed when we left the debug certificate), then install again from this page. |
| Movie will not play in the app | Install [VLC for Android](https://play.google.com/store/apps/details?id=org.videolan.vlc) → in Flashback use **Open in external player** → choose VLC. Or try a lower quality rung on the title page when offered. |
| Chooser missing / nothing opens | Install VLC from the Play Store first, then try **Open in external player** again. |

---

## iPhone / iPad

**Only the web browser version is supported on iOS.**

iOS apps cannot be sideloaded as easily or freely as Android apps. Apple’s distribution model (App Store, provisioning, enterprise/TestFlight limits) is not a practical path for this catalog. On iPhone and iPad, use Safari (or another browser) with [Setup / Open in VLC](/setup) for full-quality streams — there is no Flashback iOS app to install.

---

## Related

- [Setup for playback](/setup) — computer one-click **Open in VLC**
- [Android APK lab](/android-lab) — maintainer device checklist
- [Ownership policy](/legal) · [Why we’re doing this](/motivation)
