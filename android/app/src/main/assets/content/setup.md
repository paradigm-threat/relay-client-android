# Setup for playback

Flashback plays **full-quality movie files** in [VLC](https://www.videolan.org/vlc/) (browsers cannot play them in the page). One short setup on your computer makes the **Open in VLC** button work.

You only need to do this **once per computer**.

---

## Step 1 — Install VLC

Download and install VLC from the official site: **[videolan.org/vlc](https://www.videolan.org/vlc/)**.

Open VLC once so macOS / Windows finishes any first-run prompts.

---

## Step 2 — Enable one-click Open in VLC

### Mac

1. Open **Terminal** (press `Cmd + Space`, type `Terminal`, press Return).
2. Copy the line below, paste it into Terminal, press Return:

```bash
curl -fsSL https://flashbackrepository.org/scripts/vlc-protocol/install-macos-online.sh | bash
```

3. When you first use **Open in VLC** on the site, if your Mac asks which app should open the link, choose **Flashback VLC Protocol** and allow it to remember that choice.

That is the whole Mac setup. No test video, no extra downloads.

Direct download of the same installer (advanced): [install-macos-online.sh](/scripts/vlc-protocol/install-macos-online.sh)

### Windows

1. Install VLC (Step 1).
2. Open **PowerShell**.
3. Paste this line and press Enter:

```powershell
irm https://flashbackrepository.org/scripts/vlc-protocol/install-windows.ps1 | iex
```

### Linux

1. Install VLC (e.g. `sudo apt install vlc`).
2. In a terminal:

```bash
curl -fsSL https://flashbackrepository.org/scripts/vlc-protocol/install-linux-online.sh | bash
```

### Phone / tablet / Android TV — Flashback app (recommended)

Browsers cannot play our full-quality HEVC MKV files. The **Flashback Repository** Android app can. It is sideloaded on purpose — there is no realistic Play Store path for this catalog ([why](/motivation#no-play-store-path)).

1. On your phone, tablet, or Android / Google TV, open this link (or ask someone to send it):  
   **[Download Flashback APK](/downloads/flashback.apk)** (~14 MB)
2. If Android asks whether to allow installs from the browser or Files app, tap **Allow** / **Settings** and turn on install permission for that app, then go back and open the download.
3. Tap the downloaded **flashback.apk** → **Install** → **Open**.
4. Pick a title → tap **OK — I confirm** (ownership affirmation).
5. The movie should start in the app (it may rotate to landscape for a larger picture).

**“There was a problem parsing the package”** (Hisense / Google TV and some phones): the file you downloaded was probably not a real APK (often an HTML/JSON error page saved as `.apk`). Re-download from the link above and confirm the file is about **14 MB**; if the TV browser still mis-saves it, copy `flashback.apk` from a computer via USB.

**“Unsafe app blocked” / Play Protect** (common on first install from outside the Play Store):

This warning is normal for apps that are not on the Play Store. We cannot turn it off entirely without publishing there. The download is a normal **release-signed** APK from this site — not a random mystery file.

1. On the warning screen, tap **More details** (or **Learn more**).
2. Tap **Install anyway** / **Install without scanning** (wording varies by Android version).
3. If it still blocks: open **Play Protect** in the Play Store app (profile → Play Protect → settings gear) and temporarily allow installs from this source, or turn off “Scan apps with Play Protect” only while you install, then turn scanning back on.
4. Open the APK again and finish **Install**.

You only need to do this once per phone for this app.

**If you already installed an older Flashback build** and the new one will not update: uninstall the old app first, then install again (signing key changed when we moved off the debug certificate).

**If the movie will not play in the app** (some older phones lack HEVC hardware):

1. Install [VLC for Android](https://play.google.com/store/apps/details?id=org.videolan.vlc) from the Play Store.
2. In Flashback, tap **Open in external player**.
3. Choose **VLC**. If Android asks “Just once” or “Always”, pick whichever you prefer.

You do **not** need a computer or Terminal for Android.

Maintainer / device matrix: **[Android APK lab](/android-lab)** (checklist + in-app **Lab** screen).

---

## Step 3 — Watch a title (computer browser)

1. Open a film on Flashback.
2. Confirm you own a legal copy of the original (once per title on this browser).
3. Tap **Open in VLC**.

If the button does nothing, use **Copy stream URL**, then in VLC: **Media → Open Network Stream… → paste → Play**.

---

## If something goes wrong

| What you see | What to do |
| --- | --- |
| Mac asks which app | Choose **Flashback VLC Protocol** |
| “VLC not found” | Install VLC, then run the Step 2 command again |
| Button does nothing | Use **Copy stream URL** → VLC → Open Network Stream |
| Android “blocked install” | Allow installs from Files / Chrome, then open the APK again |
| “Unsafe app blocked” / Play Protect | More details → Install anyway (see Android steps above). Normal for sideload; we are not on Play Store. |
| Android in-app play fails | Install VLC → **Open in external player** |
| Android chooser missing | Install VLC from the Play Store first |

Uninstall (optional): [macOS / Linux helpers](/scripts/vlc-protocol/install-macos.sh) live under `/scripts/vlc-protocol/` — see `uninstall-linux.sh` / `uninstall-windows.ps1` in that folder.

---

## Advanced (editors only)

Clone the **project catalog** (Kdenlive projects, subtitles, READMEs — not the big movie files):

```bash
git clone git://iad1.relaygateway.net:9418/fan-edits.git
```

Fallbacks: `ord1` / `hkg1` on the same port, or GitLab (`git@gitlab.com:clevertree/fan-edits.git`). Feature `.mkv` files stay on IPFS and stream after ownership confirm — see [Ownership](/legal) and [Submit](/submit).

**Media root CID** (whole thin-media DAG): `bafybeid7zmxfovcgpebtalojong2fw5nc7hnle2bxh2g7ihu74sbfigoya` — also in repo `IPFS_ROOT` / `assets.manifest.json` (`mediaRoot`). [Inspect](https://cid.ipfs.tech/#bafybeid7zmxfovcgpebtalojong2fw5nc7hnle2bxh2g7ihu74sbfigoya).

Optional browser extension (does **not** replace Step 2): [Open in VLC](https://chromewebstore.google.com/detail/open-in-vlc-media-player/ihpiinojhnfhpdmmacgmpoonphhimkaj).
