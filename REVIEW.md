# Search AI APK v2 — Review

A native Android version of the standalone Search AI app, built from the
v1 code plus Claude's v1 review (Search-AI-v1-CLAUDE-REVIEW.md). Same app,
same package, same keystore as v1, so it installs over v1 and keeps saved
words and history. This is a code and build review document for the next
improvement round, not a user manual.

## What changed from v1

All 13 review bugs, fixed:
- System Back no longer exits the app from a non-search screen. A back
  stack returns to the previous screen; Back from a tab goes to Search;
  Back from Search exits, as before.
- build.sh can no longer hide compile errors: set -euo pipefail, plus
  -bootclasspath and -Xlint:-options, so the javac line needs no grep
  filter. R8 now runs with --release.
- No stale translation on direction change: there is no direction toggle
  any more. Direction comes from the query script (Persian range
  U+0600 to U+06FF), so a history tap can never run a wrong-direction
  lookup.
- A misspelling that the service echoes back is treated as no result,
  never shown as a Persian meaning.
- HTTP errors now say "Translation service unavailable, try again
  later" instead of "Needs internet". Connections disconnect and readers
  close in finally blocks.
- The scroll position resets to the top on every screen change.
- Data fixes at the source (words.json) and regenerated: Resilient's
  doubled word in the Persian example is gone, Illuminate's antonym is
  "Obscure", Lucky's synonyms and antonyms are capitalized.
- History and saved entries are case-insensitive, and an exact match
  stores the canonical word, so "austere" and "Austere" never split.
- Full configChanges list, so dark-mode toggle, font scale, and
  multi-window no longer wipe the result and card state.
- Buttons have ripple press feedback. The keyboard Search action runs
  the lookup and hides the keyboard.
- Un-saving a word clears learned. "Use for answers" is disabled while
  its provider switch is off. Similar words now match when the query
  contains the word, or within edit distance 2.

Navigation stack (review Q1): Save and Learned stay in sync through view
tags and a sync method, so going Back never re-renders the result and
never collapses revealed FA text. The card Back button uses the same
system Back path.

Robust translation (review Q2): the parser moved into its own class
(GtxParser) using org.json, skipping null segments, returning a typed
result (OK, EMPTY, OFFLINE, HTTP_ERROR). gtx stays first; MyMemory
(keyless) is the fallback. Successful translations cache under tc::
keys, capped near 200 entries, so re-lookups work offline. Queries cap
near 500 characters before sending.

Offline Persian to English (review Q5): a reverse index built at
generation time from definitionFa only (91 normalized keys for 35
words). A Persian query that matches shows the full word card, so Save
works. Shared keys show a disambiguation list. A miss falls through to
the network and caches under the fa direction.

Storage (review Q3, partial): saved, learned, and history now live in
in-memory sets loaded once and written through, so rendering no longer
re-parses the stored strings several times. SQLite is NOT in v2 (see
"Left for v3").

Tests (review Q4): the Android-free logic moved into plain classes
(GtxParser, WordIndex, ListCodec) with 15 JUnit 4 tests: parser fixtures
(single and multi-segment, escaped quotes, null segments, empty input,
HTML error body, truncated JSON, echo), lookup (exact, similar, script
detection, normalization, reverse index), list round-trip, and data
integrity (35 words, no case-insensitive duplicates, no empty fields,
Persian fields in Arabic script, no adjacent duplicate tokens,
synonyms/antonyms capitalized, reverse index sorted and in range).
All 15 pass. A verify.sh build gate checks signature plus certificate
match to v1, badging, banned strings, and resources.arsc Stored.

The word generator (gen-searchai-words.py) is committed in this source
so the build is reproducible: fix words.json, run the generator, build.

## Screens

Search, result, Show card, Saved Words, History, Settings. One activity,
programmatic layout, no pictures in v2. The direction toggle is gone;
direction is detected from the query. Settings still holds all provider
cards and collects no credential.

## Verification evidence (build tier only, not phone-tested)

- File: Search-AI-v2.apk, 37,273 bytes, package com.sobhan.searchai,
  versionCode 2, versionName 2.0.
- apksigner verify: exit 0, 1 signer, CN=SobhaN. Certificate SHA-256
  matches v1, so v2 installs over v1 and keeps its data.
- aapt2 dump badging: package com.sobhan.searchai, sdkVersion 24,
  targetSdkVersion 34, INTERNET permission only, label Search AI,
  launchable activity com.sobhan.searchai.MainActivity.
- Dex scan: 0 matches for the banned strings, case-insensitive.
- resources.arsc is Stored, as targetSdk 30+ requires.
- JUnit: 15 tests, all pass.
- Separation: Search-AI-v1.apk is untouched, and the vocab APK
  (com.sobhan.vocabbuilder, 8,468,043 bytes) is untouched.
- Build warnings: 1 non-fatal javac note (onBackPressed is a deprecated
  API on newer targets; the override still works at this target).
  No build errors.

## What v2 does NOT do yet

- No live AI answers. The Settings provider choice is stored for a later
  version; v2 never calls an AI API and never asks for a credential.
- No pictures in the Show card. The card is text only.
- Only the main definition and main example are shown. Extra examples,
  overview, background, and etymology are not bundled yet.
- No another-meaning swap. Words with several meanings show only the
  main one.
- History has Clear only, no per-item remove.
- Saved words and history live only on the phone in this app's own
  store. They do not sync with the web app.
- The machine translation still uses unofficial no-credential endpoints
  (gtx first, MyMemory fallback). MyMemory's anonymous rate limit was
  not verified; if it rate-limits, the app reports "service unavailable"
  rather than a wrong result.
- No Play Store publishing route. This is a standalone native app.

## Left for v3, with reasons

- SQLite migration (review Q3 second half). The in-memory sets already
  fix the repeated-parsing hot path, and a SQLite import touches every
  read and write path plus a one-time migration. That needs its own
  phone test, so it stays out of v2 rather than risking saved data.
- The review's "austerity" example does not hold: the edit distance
  from "austere" to "austerity" is 3, not 2, and neither string contains
  the other, so "austerity" still finds nothing in v2. The threshold
  stays at 2 exactly as specified; raising it would add false matches
  and needs its own decision.
- Long-list recycling (ListView) for saved/history. The lists are small
  at this scale; the in-memory sets plus the existing layout are enough
  for v2.

## Build notes

- Working files: searchai2-src (MainActivity.java, WordsData.java,
  GtxParser.java, WordIndex.java, ListCodec.java),
  AndroidManifest-searchai2.xml, res-searchai (via searchai2-src/res),
  build-searchai2.sh, verify-searchai2.sh, all under the android-app
  directory. Per-version backup: searchai2-backup-src.
- WordsData.java is generated from words.json by gen-searchai-words.py;
  the generator is part of the pushed source because the pushed source
  already contains the generated file.
- Signing: the working build script reads the keystore password at build
  time from the existing local vocab build script and never writes it
  into a new file. The repository copy of the build script (build.sh in
  the staged source directory) differs deliberately: it takes the
  password from an environment variable and uses repository-relative
  paths, so it runs without any local file the repo does not contain.
- The staged source directory contains source, manifest, resources, the
  repository build script, the verify gate, the generator, the tests,
  and this review. It contains no keystore, no password, and no APK
  binary.
