# Submissions

Flashback Repository is open to **community cuts** — carefully, and under the same ownership rule as everything else here: you must own a legal copy of the commercial original.

## Prefer project files over finished exports

You can send a finished export as a **reference**, but the preferred path is that I (or a merge request) **re-render from source**.

**Use open-source editors.** Submissions should be editable with free/libre tools so anyone can open the project, tweak the cut, and re-render — no proprietary NLE required.

**Accepted open-source NLEs (examples):**

- **Kdenlive** (`.kdenlive`) — preferred here; MLT / **melt** render path matches this catalog  
- **Shotcut** — same MLT framework as Kdenlive; native multi-format timeline, often without pre-converting clips  
- **OpenShot** — beginner-friendly drag-and-drop editor; quick learning curve, 3D titles, simple sequencing  
- **Flowblade** — lightweight multi-track NLE for Linux; fast workflow and magnetic-style timeline options  
- **Pitivi** — GStreamer-based Linux editor with a clean, streamlined interface  
- **Olive** — open-source project focused on node-based editing and color correction  

Other open-source editors are welcome if the project files (and any linked media formats) can be opened and re-edited with OSS tooling.

**Please avoid closed-source-only project formats** (for example Premiere, Final Cut, DaVinci Resolve project packages) when there is no practical open-source way to open or continue editing them. A finished `.mkv` / `.mp4` as a *reference* is fine; locking the cut inside a proprietary project is not. If you must start from a closed tool, export a clear cut list / EDL / notes and prefer redoing the timeline in one of the editors above before submitting.

## Get the catalog locally first

Before you open an MR (or email a patch), clone the same tip the site serves so your paths match production:

```bash
# anonymous Relay peer (iad1 origin)
git clone git://iad1.relaygateway.net:9418/fan-edits.git
cd fan-edits
```

Or with a GitLab account (needed to push / open an MR):

```bash
git clone git@gitlab.com:clevertree/fan-edits.git
# or: git clone https://gitlab.com/clevertree/fan-edits.git
```

More detail (peer fallbacks, what is / isn’t in git): [Setup — Clone the catalog](/setup#clone-repo).

On each title page, the **Project files** section lists downloadable project files (often `.kdenlive`), `.srt`, and other small tip blobs — useful if you only need one cut without a full clone.

## How to contribute

1. **Email** an open-source project (+ notes), or a finished file as reference only.  
2. Or open a **merge request** on [GitLab: clevertree/fan-edits](https://gitlab.com/clevertree/fan-edits) under `FanMixes/` or `KidFriendly/`.  
3. Include a short **audience** README: why the cut exists, what changed for viewers, and any music / remix credits.

**Title READMEs stay non-technical.** Build steps, render commands, local paths, editor flags, and cut-list tooling belong on [Submit](/submit) and [Setup](/setup) — not on the movie page. Link here when someone needs to contribute or rebuild.

**Fixes on existing edits** (typos in a README, subtitle timing, a bad cut note) are welcome the same way — small MRs against the title folder are fine. Step-by-step “open an MR” docs will land here soon.

## What helps a submission land

- Cuts that serve a clear motivation (tone, violence, pacing, plot clarity, family viewing, etc.)  
- Joins QC’d on transition frames where possible  
- Credits for any third-party music (game remixes welcome when they fit)  
- No claim that the edit replaces buying the studio release  

## Music and remixes

Whenever it fits, titles here weave in **video game remixes** — in credits and sometimes in the body of the film. If your cut does the same, document sources so we can credit correctly. Remix artists: if something should be linked, renamed, or removed, say so and we’ll fix it.

## Contact

**Site bugs / public notes:** use the [Board](/board) (also linked from the theater “Report a problem” control). Posts are public markdown on the git `comments` branch.

For **cut submissions**, use the address you already have for Ari / Flashback, or GitLab issues / MRs on the repo above.
