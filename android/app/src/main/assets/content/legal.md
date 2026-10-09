# Ownership and fan-edit policy

## You must own the original

Before playing any title in the Flashback Repository, you must **own a legal commercial copy** of the corresponding original film (or other lawful access that your jurisdiction treats as ownership for personal use).

This site publishes **personal fan-edit projects** — alternate cuts and mixes made from sources the editor owns. It is not a substitute for buying, renting, or otherwise obtaining the studio release.

That ownership rule is the long-standing precedent used by fanedit communities and related forums: the edit is for people who already have the movie. Stream and download links for feature media stay behind that disclaimer on this site.

## Where copyrighted media lives (and does not)

We try to stick to the ownership rules as they are commonly understood in fanedit communities:

- **Git / public repos do not hold the movie files.** Catalog history, CleverScript, READMEs, cut notes, and **text-only** pointers (content hashes and paths) live in the repository. **Kdenlive project files** and similar may mention source **filenames** as plain text so the edit stays reproducible. A filename or hash in a text file is not the film, and is not a way to pirate the film by itself.
- **Feature video (the copyrighted edit masters) is stored on the IPFS network**, addressed by content hash — not checked into any public git remote as binary media.
- **Servers may cache IPFS blocks temporarily** so streams and seeks work with `Range` requests. That is a **network cache**, not a permanent public file locker and not a substitute for “the movie lives forever on this disk for everyone.” Cache can be garbage-collected; availability depends on peers who still pin the CID.
- **Access on this site is gated.** Theater stream / download chrome is for visitors who have confirmed ownership via the disclaimer. This is a personal catalog under that rule — not an open pirate mirror.

If you do not own the original, do not play the edit. Confirm ownership on the disclaimer when you first visit, and again whenever that acknowledgment is cleared.

## What this site is

- A catalog of personal projects (notes, cut lists, and playback when media is seeded on IPFS)
- Hosted on the Relay network with **thin IPFS** for large video files
- Operated as a private / personal library surface, not a commercial streaming service

## What this site is not

- Permission to distribute studio masters
- A claim that every jurisdiction treats fan edits the same way
- A promise that every CID stays pinned on every node forever
- Legal advice

See also [Terms](/terms) and [Why we’re doing this](/motivation).
