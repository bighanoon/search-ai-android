# Search AI APK v4 — review notes

## What v4 is

An exact-visual-match rebuild of the standalone Search AI app against the
user's WEB screenshots, answering the v3 phone rejection ("nothing look
like the web app"). Same app, package `com.sobhan.searchai`, versionCode 4,
versionName 4.0, same debug keystore and same `searchai` store as v3, so
it installs over v3 and keeps saved words and history.

Visual targets (read as images, not descriptions):
- Web home: `user/files/1000040908_10_t5fz.jpg` and
  `user/files/1000040917_17_kkh9.jpg`
- Web result page for "yelling": `user/files/1000040919_16_k79o.jpg`
- Web Show card for YELLING: `user/files/1000040921_15_pi69.jpg`
- v3 phone screenshots (what NOT to repeat):
  `user/files/1000040915_13_awo4.jpg`,
  `user/files/1000040913_14_otrk.jpg`

## The 8 web-versus-v3 differences, and how each was fixed

1. Buttons and colors: v3's purple accent (`0xFF9B8CFF`) and full-width
   purple Look up block are gone. The accent is now the web's dark brick
   red `0xFF65271D`, sampled twice from the result screenshot, used for
   the small Look up pill only. All other pills are dark `0xFF26262F`.
2. Chips versus rows: Recent searches and Try a word are now small inline
   wrapping chips (`FlowLayout`, several per row), with the remove X ON
   the Recent chip itself. No more full-width rows or separate remove box.
3. History and Settings: small right-aligned dark pills with drawn icons
   (clock, gear), not large side-by-side buttons.
4. Input: no separate heading plus input box. One large transparent
   EditText (34sp bold, dimmed `0xFF8A8580` hint "Type any word...") with
   the Look up pill, English ⇄ Persian direction indicator, FA label,
   and speaker inline in the row below it.
5. My Words cards: word bold 18sp with Persian below on the left; Saved
   (dark pill, dim text) or Learned (green text) pill plus CEFR as plain
   bold text with NO pill background on the right, matching the web.
   CEFR only for the six verified levels: Favoritism C1, Interpretation
   B2, Helpful A2, Transcendence C2, Eloquent C1, and now yelling B1
   (transcribed from the result screenshot). All other words show no
   badge, never an invented level.
6. Information given out: the result page is now the web's full stack —
   word header with X, scrollable tabs (Translation, Definitions,
   Examples, Synonyms, Similar), "New word" + CEFR row, TRANSLATION card
   (Persian plus alternatives list), "All definitions" and "Similar word"
   buttons, word card (word + speaker, pos + definition + FA pill, Save
   + Show card with bookmark/image icons), MEMORY HOOK card, full-width
   Mark learned, archive note, "No AI selected" note. The Show card adds
   the LEARNING badge, "THE MEANING, IN ACTION" caption + scene text,
   example inset + FA pill, gold Memory hook label, action row
   (add / heart / share / swap N/M / Tell Me More), the swap hint, and
   Close. Tell Me More opens a detail view with only the sections the
   app can provide (definitions, examples, synonyms, hook, similar).
7. Theme: page `0xFF0B0A0F`, cards `0xFF1C1C24`, insets/chips
   `0xFF26262F`, text white, secondary `0xFFA3A3AD`, green
   `0xFF34D399`, teal `0xFF2DD4BF`, blue `0xFF3B82F6`, gold `0xFFFBBF24`,
   purple `0xFFA855F7` (LEARNING badge only). No purple-tinted cards
   or buttons remain.
8. Icons: all drawn on canvas (`IconView`: speaker, clock, gear,
   bookmark, image, share, add, heart, swap, check) — no emojis. The
   only typographic marks are the web's own × (chip remove, header X)
   and › (Similar word button).

## The rebel bug: root cause and fix (proven, not guessed)

v3's `DictParser.parse()` read only `root[0]` of the dictionary
response. The live API returns TWO entries for "rebel": a noun entry
(2 definitions, no examples, no synonyms/antonyms) and a verb entry
(1 definition "To resist or become defiant toward an authority.",
synonyms ["defy"], antonyms ["obey", "submit"], also no example).
v3 dropped the whole verb entry, so the phone card showed exactly 2
bare meanings and no synonyms/antonyms. v4's parser loops over ALL
root entries and merges meanings, definitions, synonyms, and antonyms;
each definition now also carries its own part of speech, which survives
the JSON cache round-trip (new `p` field, with fallback to the entry
pos for pre-v4 caches). Test `dictParseMultiEntry` pins the real rebel
shape. Note: rebel genuinely has NO example in this source, so v4
shows the honest "No example available for this word." note for it
rather than inventing one; examples appear whenever the source
provides them.

Also fixed in passing: v3's misleading "No offline entry" line, which
stayed visible above a fetched card, is removed entirely.

## Kept from v3 (no regressions)

dictionaryapi.dev full entries (keyless) with `dict::` persistence for
offline repeat lookups; Save filing the full entry; FA tap-to-reveal
throughout; instant Save; back stack with tag-synced pills (FA reveal
survives Back); script-based direction detection; echo treated as no
result; "service unavailable" error text; scroll reset; case-insensitive
history/saved; full configChanges; ripple feedback; keyboard Search
action; org.json parser; MyMemory fallback; translation cache (capped
200); in-memory sets; Persian reverse index; tests; verify.sh.

## What v4 does NOT do (honest limits)

- NO picture panel on the Show card. The web card's generated
  illustration cannot be matched by a key-free APK (no image
  generation, no bundleable picture for new words). The caption plus
  scene text appear as a text panel; there is NO Repaint button and NO
  "Picture doesn't match the meaning? Repaint it" link, because both
  would be dead without a picture. Never a fake picture.
- Show card counter reflects APK-available meanings only (yelling 1/2,
  rebel 1/3), NOT the web's 1/4 — the web's other two yelling meanings
  are not visible in the screenshots and cannot be invented.
- Show card default meaning is the result-page (primary) meaning, even
  though the web Show card screenshot shows the short meaning as
  "Meaning 1". v4 keeps one saved meaning index per word for
  consistency between the result page and the card.
- LEARNING badge is presentational (it matches the reference card's
  state, not a server status). Heart likes are local-only (stored in
  prefs), not a server count.
- FA reveal state is lost on the single re-render that happens when
  returning from a Show card after a meaning swap (the result must
  re-render to show the newly chosen meaning). All other Back
  navigation preserves FA reveal state.
- No Gmail sign-in, no OAuth, no cloud code, no live AI answers, no
  web-app sync. MyMemory's anonymous rate limit is unverified; if
  rate-limited, the app reports "service unavailable" rather than a
  wrong result. A dictionary 404 (word the source lacks) shows the
  translation plus "Full entry not available.", never a fake entry.
- The swap saves one meaning index per word; the web's per-card swap
  state is not separately tracked.

## Verification (build tier only — NOT phone-tested)

- `build-searchai4.sh`: javac (source 8, android.jar bootclasspath) +
  aapt2 link + stable R8 dex + zipalign + apksigner. One non-fatal
  note only: the onBackPressed override is deprecated at this target
  but still works.
- JVM tests: 25 pass (15 carried from v2, 5 new in v3, 5 new in v4:
  dictParseMultiEntry, dictPosRoundTrip, verifiedYelling, hookFor,
  cefrYelling). FlowLayout, IconView, and MainActivity are Android-only
  and are excluded from JVM tests, as in v3.
- `verify-searchai4.sh`: GATE PASSED — apksigner verify OK (CN=SobhaN),
  certificate SHA-256 `e1733f5112c8ac821d71e9b5cfa4593d9b33c74a7e2b3f9308fadfc88faf22fd`
  matches v3, badging package `com.sobhan.searchai` versionCode 4
  versionName 4.0 sdk 24 target 34 INTERNET only label "Search AI",
  dex banned-string scan 0, resources.arsc Stored, vocab APK untouched.
- Source, manifest, resources, and tests scan: 0 banned brand names
  strings. Staged source is diff-verified byte-identical to the
  working source (build.sh/verify.sh/REVIEW.md are repo-adapted by
  design). Staged dir contains no keystore, no password, no APK.
- v1 (33,176 bytes), v2 (37,273 bytes), v3 (45,465 bytes) untouched by
  size and date. v4 is 53,657 bytes.

## Phone test list owed

Install over v3 with saved words and history surviving; home matches
the web home screenshot (input-as-heading, small Look up pill, small
History/Settings pills, wrapping chips with X on the chip); chip
remove; "outlaw" online to full card with FA plus instant Save;
"outlaw" offline on repeat to full card; "rebel" to full card with
synonyms/antonyms and the no-example note; "yelling" to the verified
web entry (B1, Persian plus alternatives, hook) plus Show card, swap
through both meanings, and Tell Me More; Saved Words to full entry;
Back from card/detail/saved/history/settings; FA surviving Back;
rotate plus dark-mode toggle.
