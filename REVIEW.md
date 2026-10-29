# Search AI APK v5 — review notes

## What v5 is

Version 5 of the standalone Search AI app, built on v4 (not a restart).
Same app, package `com.sobhan.searchai`, versionCode 5, versionName 5.0,
same debug keystore and same `searchai` store as v4, so it installs over
v4 and keeps saved words and history. It answers two user reports on v4:

1. The select-to-translate tip showed but did nothing: v4 marked text
   selectable but had no selection handling and no popup code at all.
2. After a search, the card must carry all dictionary information, not
   just the main meaning.

Targets read as images, not descriptions:

- Popup target (web app's select-to-translate popup):
  `user/files/1793314363678_20_f0bp.jpeg`
- v4 phone screenshot with the broken tip circled:
  `user/files/1793314200273_19_39cj.jpeg`

## Part A — select-to-translate popup

Layout matches the target bottom card: selected word bold top-left,
circular X top-right, translation large and right-aligned for Persian
(left-aligned for English), alternatives smaller on the same alignment
below it, button row Save (bookmark icon), Hear (speaker icon), Copy,
More, Close. No Translate button inside the popup; the translation
appears instantly.

Implementation:

- New `SelTextView` (a TextView subclass reporting non-empty selection
  changes, deduped, resetting when the selection collapses so
  re-selecting the same word fires again). Content text uses it: home
  headings, tip text, My Words words, saved/history rows, card
  definitions, examples, hooks, translation text, alternatives, the
  word-card word, Show card scene/hook/example, detail hook/example,
  and ChatGPT section bodies. Button and chip labels stay plain
  non-selectable TextViews, as on the web. Popup views are plain
  TextViews, never SelTextView, so selecting popup text cannot
  retrigger the popup.
- The popup is a full-screen overlay added via `addContentView`
  (transparent dismiss view plus the bottom card), so it works from
  any screen without restructuring the root layout. It hides on every
  screen change and on every new lookup, so a stale popup never
  overlays a new screen. Close, X, and an outside tap all dismiss it.
- Fast path only, never a key, never an AI call: bundled entry first
  (English word -> stored Persian; Persian selection -> reverse-index
  hits, first hit as the translation, further hits as alternatives),
  then the translation cache, then keyless machine translation (the
  existing gtx path with MyMemory fallback). Alternatives come from
  MyMemory's matches array, filtered (no empties, no main translation,
  no echo, no duplicates, capped at 5) and filled in when ready; the
  alternatives line stays hidden when the source gives none. A
  sequence guard drops stale responses when the user selects another
  word before an earlier fetch returns.
- Save files the word into Saved Words instantly and syncs the
  result/card pills; it also caches the translation under the saved
  word so opening it later shows the translation. Hear reuses the
  existing speech path (Persian voice for Persian text, English voice
  otherwise, on-device, no network). Copy copies the translation (or
  the word while translating) and briefly shows "Copied".
- More dismisses the popup, then runs the normal lookup for the
  selected word and files it into Saved Words, so the full card is
  created, not just previewed. With a ChatGPT key saved it includes
  the ChatGPT sections; without one it is dictionary only. A word
  with no dictionary entry shows its translation plus a plain note.
- The operating system's own selection toolbar (Translate, Copy,
  Select all) appears naturally from selectable text and coexists
  with the app popup, exactly as in the target screenshot; the app
  neither adds nor removes it.

## Part B — full dictionary card

- The dictionary parser already merged all root entries and both
  meaning-level and definition-level synonyms/antonyms; v5 raises its
  caps (definitions 8 -> 30, words 12 -> 30) so realistic entries are
  never truncated, and the JSON cache round-trip preserves every
  field.
- Synonyms and antonyms changed from chips plus one joined FA pill
  (which translated the whole list as a single phrase) to one row per
  word: the word (tap to search) plus its own FA pill (tap for that
  word's Persian). Bundled synonyms use their stored Persian
  instantly; others translate on tap and cache. Definitions and
  examples already had individual FA pills; those are unchanged.
- The result stack (word card plus Definitions, Examples, Synonyms,
  Similar sections, plus All definitions) therefore carries every
  definition, every example, every synonym, and every antonym the
  source provides, each with FA tap-to-reveal, plus Save, Show card,
  and Mark learned. The word card itself is not crammed with the
  sections; they live in the stack below it, preserving the web
  layout.
- Verified hooks (AUSTERE and yelling only, user-provided text) still
  appear where available. No other hook, overview, or background text
  is ever invented; a section with no source data shows a plain note.

## Settings-only ChatGPT API option

Default is key-free: dictionary card only, no banners, no prompts, no
key field on any screen except Settings. Settings gains one ChatGPT
key card: an empty password input plus Save when no key is saved; a
masked label (last 4 characters only) plus Change and Remove when one
is saved. Change shows an empty input, never the saved key. The key
lives only in the app's own store, is never logged, and is never
shown in full.

When a key is saved, the result card gains ChatGPT sections (overview,
background, memory hook, AI answer) from the real API (gpt-4o-mini,
JSON mode, low temperature, bounded tokens, 15s connect / 25s read
timeouts, per-word cache). Validation matches the web app: unparseable
or wrongly typed top-level JSON falls back to the dictionary card;
a missing or empty field omits that section only. Each section carries
a small "ChatGPT" tag so ChatGPT-written content is distinguishable
from dictionary content. A verified hook keeps its own MEMORY HOOK
card; the ChatGPT hook only fills the gap. Removing the key hides the
sections even if cached. A small status note appears only when a key
is saved and the call fails; with no key saved there is no note at all.

The provider on/off toggles and "Use for answers" pills from v4 remain
in Settings, unchanged, but content is now gated by the key alone.

## Word-file export

A "Word file" button (download icon, distinct from the generic share
icon) sits on the word card and on each Saved Words entry. Tapping it
writes one JSON file for the word and opens the Android share sheet,
so the user can save it to Downloads or send it via Telegram,
WhatsApp, Drive, or email. It works offline (the entry is already
local) and needs no storage permission: the file is written to the
app cache and served by a minimal read-only content provider
(`WordFileProvider`, no library) with a per-URI read grant.

File name: `<word>.json`, lowercase, spaces replaced by hyphens
("heads-up.json"). Contents are exactly what the app holds, missing
sections omitted, never faked: word, parts of speech, definitions
(each with its examples), synonyms, antonyms, Persian plus Persian
alternatives, phonetic if present, memory hook if actually available,
"meaning in action" scene text if present, CEFR if verified, source
(bundled, dictionary, verified, or translation), and the date
searched (recorded on every search from v5 on; older words fall back
to the export date). Translation-only words hold the translation plus
a plain note. The file holds word data, not a picture: no image is
ever embedded.

## Kept from v4 (no regressions)

Web home shape, brick red Look up pill, dark pills, inline wrapping
chips with X on the chip, small History/Settings pills with drawn
icons, transparent input, My Words cards, full result stack (tabs,
New word + CEFR, TRANSLATION card with alternatives, All definitions,
Similar word, word card with icons, MEMORY HOOK card only when a hook
is actually available, Mark learned, archive note), Show card
(LEARNING badge, caption + scene text, example inset, gold hook
label, action row, swap N/M, Tell Me More with providable sections
only, Close, NO picture panel, NO Repaint button), dictionaryapi.dev
full entries with `dict::` persistence, instant Save, back stack with
syncButtons, script detection, echo-as-no-result, translation cache,
reverse index, tests, verify gate. Credit by SobhaN. Zero banned
brand-name strings in source, manifest, or dex (verified by scan;
the only accepted hit is verify.sh's own grep pattern).

## What v5 does NOT do

- No picture panel and no Repaint button anywhere (key-free; the
  Show card keeps its caption + scene text panel). Pictures arrive
  with v6, not here.
- ChatGPT sections exist only when the user voluntarily saves a key
  in Settings; by default the app is dictionary only and never asks.
- No Gmail/Google sign-in, no cloud sync, no Play Store route. Saved
  words and history stay on the phone.
- No live AI answers other than the optional ChatGPT sections above.

## Verification (build tier only, not phone proof)

- `build-searchai5.sh`: aapt2 compile/link, javac, stable R8 dex,
  zipalign, apksigner sign. One compile error on the first pass
  (`TextView.setSelection` is not in the public SDK stubs); fixed by
  resetting the selection-report dedup plus `clearFocus` instead.
  Final build clean apart from the pre-existing onBackPressed
  deprecation note.
- `verify-searchai5.sh`: GATE PASSED — apksigner verify, certificate
  SHA-256 matching v4, badging (package `com.sobhan.searchai`,
  versionCode 5, versionName 5.0, minSdk 24, targetSdk 34, INTERNET
  only), dex banned-string scan 0, resources.arsc Stored, vocab APK
  untouched, v1–v4 md5-verified untouched.
- JVM tests: 36 pass (25 carried from v4, 11 new in v5: dictionary
  completeness, raised caps, JSON round-trip completeness, ChatGPT
  JSON validation, MyMemory matches parsing, mixed-script detection,
  word-file contents, phrase filenames, translation-only omissions).
- The popup and the full card were verified by code trace plus these
  tests only: selection reporting, fast-path order, sequence guard,
  Save/Hear/Copy/More/Close wiring, per-word FA rows, and word-file
  contents. The APK has NOT been installed or tapped through on the
  user's phone in this environment, and network paths (dictionary
  entry, translation fallback, speech, ChatGPT call, share sheet)
  have not been exercised on a device. The phone test decides.

## Phone test list (for the v5 delivery)

Install over v4 with data surviving; home match; select text on home
(tip line, headings, My Words word) -> bottom popup with instant
translation, alternatives where the source gives them, working Save,
Hear, Copy, More, Close, X, and outside-tap dismiss; select text on a
result page and on a Show card/detail view -> same popup; popup Save
files into Saved Words; popup Hear speaks the selected text in its
own language; popup Copy copies the translation; popup More opens
the full card for the selected word and files it into Saved Words
(dictionary sections, plus hook/scene where available, no picture,
no Repaint); a word with definitions, examples, synonyms, and
antonyms shows all of them in the card/tabs with FA tap-to-reveal; a
word the dictionary lacks shows translation plus plain note only;
Settings shows the ChatGPT key card ONLY in Settings (no key field,
banner, or prompt on search/result/card/popup screens); with no key
saved, the app behaves exactly as key-free (dictionary only, no nag);
with a key saved (only if the user chooses to test this), the card
gains labeled ChatGPT sections and falls back to dictionary on
failure; Word file on a card and on a Saved Words entry -> share
sheet -> save to Downloads / send via Telegram -> file opens as JSON
with the right fields and no picture; Back from card/detail/saved/
history/settings; FA surviving Back; rotate plus dark-mode toggle.
