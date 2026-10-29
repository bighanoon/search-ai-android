# Search AI APK v3 — review notes

Build tier only. This APK has not been installed or tapped through on the
user's phone, and the network paths (dictionary entry, translation
fallback, on-device speech) have not been exercised on a device. Phone
tests below decide whether v3 is confirmed.

## What v3 changes on top of v2

- Web home shape (from the web home screenshot): "Type any word..."
  heading; Look up pill plus an English ⇄ Persian indicator with an FA
  label and a speaker control; the select-text tip line; History and
  Settings pill buttons; Recent searches as chips with a remove button
  on each chip; a My Words section ("Words you saved or marked learned
  in Search AI, kept in one archive.") with cards showing the word, its
  Persian, a Saved or Learned pill, and a CEFR badge ONLY where the
  level is verified (Favoritism C1, Interpretation B2, Helpful A2,
  Transcendence C2, Eloquent C1 — every other word shows no badge,
  never an invented level); Try a word chips (Rebel, Nostalgia,
  Eloquent, Serendipity, Resilient). All sections use this app's own
  saved/learned/history store; nothing is copied from the web
  screenshot as data, and no sync is claimed.
- Full entries for words outside the bundled 35: when online, the app
  fetches a full dictionary entry from a keyless source
  (dictionaryapi.dev, no key, no account) and renders it in the same
  full card shape as a bundled word — definitions, examples, synonyms,
  opposites, FA tap-to-reveal throughout, instant Save. The fetched
  entry is persisted locally (dict::<word> in the same "searchai"
  store), so a repeat lookup shows the full card even offline, and
  Save files the full entry, not just the word. The dictionary fetch
  and the translation fetch run in parallel: the entry wins when it
  arrives; the translation is only the fallback, and when the entry
  wins the translation is still cached under the entry's word so the
  My Words card can show its Persian.
- Never invents content: if the dictionary source gives no entry, the
  app falls back to the translation plus a plain note ("Full entry
  not available."). If the source gives only a translation, that is
  all that is shown, plus the note. No definition, example, or synonym
  is ever fabricated.
- Speaker control: on-device text-to-speech (no key, no network call).
  Persian input is spoken with a Persian voice, English otherwise.
- Definitions and examples on bundled and fetched cards are
  selectable, so the select-text tip works on card content too.
- All v2 behavior preserved: back stack with syncButtons (Save/Learned
  in sync without re-rendering, FA reveal surviving Back),
  script-based direction detection, echo treated as no result,
  "service unavailable" error text with disconnect/close in finally,
  scroll reset on screen change, case-insensitive history/saved with
  canonical storage, full configChanges list, ripple feedback,
  keyboard Search action, org.json parser with typed result, MyMemory
  fallback, translation cache, in-memory sets, tests, verify.sh.
- No sign-in, no OAuth, no cloud code of any kind in v3. Gmail login
  plus cloud storage of saved/searched words is a separate follow-up
  after v3 and was deliberately left out.
- No live AI answers section (the web AI section needs a key the APK
  does not collect; the APK stays key-free by the user's rule, with no
  key prompts, banners, or credential fields anywhere).
- No pictures unless a word has one: bundled words have no pictures
  in the APK, and the dictionary source provides none, so fetched
  cards are text-only like v2's Show card.
- Credit by SobhaN (app header and Settings footer). Zero banned
  strings (the two forbidden brand names) in source, manifest, or dex
  (verify.sh's own grep pattern is the only match, as in v2).

## What v3 does NOT do yet

- No web-data sync: the APK shows its own saved words and history,
  not the web app's. Saved/learned words stay on the phone only.
- No live AI answers (key-free by the user's rule; see above).
- No pictures in cards (no picture source in the APK; bundled words
  have none, the dictionary source provides none).
- No another-meaning swap, no extra examples/overview/background/
  etymology sections for bundled words (only the main definition and
  example, as in v2), no per-item history remove beyond the home
  chips, no SQLite migration (in-memory sets already fix the
  repeated-parsing hot path), no long-list recycling, no Play Store
  route.
- MyMemory's anonymous rate limit is unverified; if it rate-limits,
  the app reports "service unavailable" rather than a wrong result.
- The dictionary source covers common English words; rare, misspelled,
  or multi-word entries may 404, in which case the app shows the
  translation plus the "Full entry not available." note.
- The web home's speaker icon is a speaker button here; the web
  "select any text" popup itself is not reproduced (card text is
  selectable, and the tip line describes the behavior).
- The web My Words cards show a CEFR badge for every card; this app
  shows one only for the five verified words above.

## Verification (build tier)

- apksigner verify passed; certificate SHA-256 matches v2, so v3
  installs over v2 and keeps saved words and history.
- aapt2 dump badging: package com.sobhan.searchai, versionCode 3,
  versionName 3.0, sdkVersion 24, targetSdkVersion 34, INTERNET only,
  label "Search AI".
- Dex banned-string scan: 0. Source/manifest scan: 0 (excluding
  verify.sh's own grep pattern).
- JUnit: 20 tests pass (15 carried from v2, 5 new: dictionary parse,
  dictionary reject-empty, dictionary JSON round-trip, phonetic
  fallback, verified-CEFR-only).
- v1, v2, and the SAT Vocab Builder APK are untouched.

## Phone tests owed

Install over v2 and check saved words and history survive; home looks
like the web home (heading, lookup row, tip, pills, recent chips with
remove, My Words cards, Try a word); tap a recent chip's remove button
and confirm it disappears; search "outlaw" online and confirm the full
card (definitions, examples, synonyms) with FA reveal and instant
Save; reopen the app offline and search "outlaw" again for the full
card; Save it, then open it from Saved Words for the full entry;
search a word the dictionary source lacks and confirm translation plus
the "Full entry not available." note, never a fake entry; tap the
speaker and confirm speech; Back from card/saved/history/settings;
FA reveal surviving Back; rotate plus dark-mode toggle.
