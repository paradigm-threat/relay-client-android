# Why we’re doing this

This is not only about making better versions of movies.

It is about the **allowed** part.

Right now that permission does not feel real. Craft matters — cleaner pacing, less cruelty, a score that fits — but the deeper point is agency: if you **own** a film, you ought to be able to improve your copy and share that cut with friends without treating the act itself as radioactive.

## What the craft is usually fixing

Most titles in this library are chasing one or more of these:

- **Cringe and tone-breakers** — jokes, beats, or performances that yank you out of a movie that otherwise works  
- **Needless gore and violence** — especially when it isn’t load-bearing for the story (family-safer Debugged cuts, Silent Hill, etc.)  
- **Plot holes and muddy storytelling** — trim or reshape so the spine is clearer  
- **Obnoxious edit / post-production decisions** — choppy pacing, survivor’s-guilt comedy, or “fixes” that feel hostile to a film’s original warmth (*Van Helsing* is the loud case; the early *Mummy* pictures are the quieter one)  
- **Missing or compromised releases** — e.g. *Final Fantasy: The Spirits Within* never got the international Japanese-track release many of us wanted  

Often the score gets attention too: when it fits, cuts weave in **video game remixes** — not only in credits, but inside the film (*Silent Hill — Nay Tomorrow*, Sonic DeSchwartzed OC ReMixes, Castlevania beds in Van Helsing, and more). See [Submissions](/submit) if you want to contribute a cut the same way.

## Culture as leverage

Entertainment is not only product. In periods that lead up to war, large “global resets,” and consolidation of culture, messaging control intensifies. Cinema is a soft-power surface. The industry’s incentives are not aligned with letting audiences quietly reclaim ownership of the stories they already paid for.

You do not need a conspiracy flowchart for that observation. You need a clear rule: **own the original, then you may edit.**

## Warnings then and now

There used to be FBI-style warnings on physical media. Those banners faded from the art. The chill did not disappear — it moved. Pressure still arrives from unexpected places: ISP actions, platform bans, soft legal fear. Trend lines can look better while the settled date when editing old favorites and sharing them with friends is treated as normal stays far away.

Progress is real. It is also slow. We are not there yet.

## Fanedit venues

Existing fanedit communities set useful ownership norms. They also feel deeply compromised — a **controlled opposition** form of the scene, where industry fear manages the outlet. Quality and dissent get filtered. Serious posts can be banned almost immediately even when the editor owns the original and follows posted rules. The author of this repository is banned from those venues; they are not open to people trying to stand out with serious material.

Historic leaders have described controlled opposition as a containment strategy. Applied to culture, it means a permitted sandbox that never approaches the full potential of owned-media remix. We have not begun even **1%** of that potential.

## Pushback by doing the work

Flashback Repository is pushback under ownership rules: make the cut, document it, host the **bytes on IPFS** (not as permanent public git blobs — repos keep notes, project files, and text pointers; servers may cache IPFS blocks for streaming), and require that viewers own the commercial original before unlocking stream access. Watch via **external player stream** (VLC and friends) from the theater page, or later via the Android / TV app. No pirate archive. No apology for wanting better versions of films we already bought. See [Ownership policy](/legal).

Projects stay in **open-source NLEs** (this catalog is mostly Kdenlive + melt; Shotcut, OpenShot, Flowblade, Pitivi, Olive, and similar are welcome) so the library remains editable without proprietary tools — see [Submissions](/submit).

## Why MKV + HEVC (and an external player), not in-browser Play

We publish finished cuts as **full-quality HEVC Matroska (`.mkv`)** with **embedded subtitles** — the same file the edit produces. That is the best experience for picture, audio, and captions in one container.

### Navigating the standards maze

Getting from “I own the film and I made a better cut” to “a friend presses Play in Chrome” is not a single button. It is a maze of containers, codecs, licenses, and `canPlayType` answers that differ by OS and hardware.

You would expect that maze to have thinned out by now. It has not. For a small library that just wants to host owned edits over IPFS, the path still feels deliberately hard — absurd for this decade:

- **Matroska (`.mkv`)** is what editors finish in. Browsers never made full Matroska a first-class `<video>` format. The web settled on **ISO BMFF / MP4** and **WebM**, not “open the `.mkv` and play.”
- **HEVC / H.265** is the efficient codec our finals use. Safari sometimes plays HEVC-in-MP4. Chrome and Firefox often report **no HEVC at all**, or accept a probe and then throw `NotSupportedError` on the real file.
- **In-page “WASM players”** we tried only **demux** into WebCodecs — they do not ship a software HEVC decoder, and they refuse sources larger than a tiny in-memory cap. A feature film will not fit. Demux is not watch.
- **H.264 re-encode ladders** would make in-page Play boringly reliable — at the cost of days of CPU and a second multi‑gig archive of every cut. That is how big platforms paper over the maze. It is not how a personal catalog should have to ship.
- Browsers also will not **hand a stream to VLC** with one reliable click. Custom URL schemes are flaky. The honest desktop path is **copy the HTTPS URL → Media → Open Network Stream** (or Download and open locally). Android can offer a real **Open in external player** intent.

So the maze is not “video is hard.” It is that **universal in-browser Play** was standardized around stacks and budgets that suit the largest distributors. Independents who already own the discs keep hitting container walls, codec walls, silent empty strings from `canPlayType`, and demux toys that cannot hold a movie.

It can feel like the only way incumbents could keep the open web from competing with their players: not a ban on the word *Matroska* or *HEVC*, but a thicket where “ship one best file and Play works everywhere” never quite arrives.

We stopped bending the file to the thicket. The start path that preserves quality:

1. Keep the finished edit as **one HEVC MKV** with **embedded** default (and enhanced) subtitles.
2. Host it over HTTPS / IPFS with `Range` so players can stream and seek.
3. Theater **default**: copy stream URL → open in **VLC** (or another capable player) — no endless Buffering, no fake in-page engine.
4. Keep looking for a real in-page MKV/HEVC path later; do not pretend we have one today.

The name *Matroska* nods at Russian nesting dolls (*matryoshka*). The practical barrier is standardization and product incentives, not a formal ban on the word.

The allowed part is the point. The rest is craft — and refusing to wait for the maze to become optional before we share a better cut.
