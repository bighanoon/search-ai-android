# Search AI APK v1 — Review

A native Android version of the standalone Search AI app: the user's idea
of Search AI as its own smaller, faster app, separate from the main vocab app.
Built with the android_apk_build skill (JDK 21, Android SDK 34, stable R8,
same debug keystore as the vocab app). This is a code and build review
document for the next improvement round, not a user manual.

## What v1 includes

- Search screen: a word field, a Look up button, and an EN to FA / FA to EN
  direction switch. The Persian meaning comes first in every result.
- Result view for bundled words: the word in capitals, part of speech and
  phonetic, definition, example in a dark inset, close words, and opposite
  words. Every Persian translation sits behind a small FA button and stays
  hidden until tapped, never shown by default.
- Save: tapping Save flips it to Saved instantly and files the word into
  Saved Words. Tapping Saved again removes it. No save flow asks for any
  credential and no save flow shows any credential banner.
- Saved Words screen: the saved list, with learned words marked. Tapping a
  saved word opens its Show card.
- Show card screen: the word card for a saved word, with definition and
  example plus FA reveal, close and opposite words, Save, Mark learned,
  and a Back button that returns to where the card was opened from.
- History screen: looked-up words in newest-first order, tap to look up
  again, plus Clear history.
- Settings screen: provider cards for Gemini, ChatGPT, Perplexity, Claude,
  and Grok, each with an on/off switch and a Use for answers choice. All
  provider UI lives only here, never on the search page. Settings stores
  the choice; it collects no credential.
- Offline bundle: the 35 deck word entries from words.json, generated into
  WordsData.java with no invented text. Exact matches win; anything else
  shows Similar words from the bundle.
- Out-of-bundle words: when online, the app fetches a machine translation
  with no credential and shows it behind the FA button. When the fetch
  fails it shows a plain needs-internet note. Out-of-bundle translations
  can be saved, and their Show card shows the saved translation with a
  note that the full entry is not in the offline set.
- Dark look throughout, credit by SobhaN in the header and Settings.

## Screens

Search, result, Show card, Saved Words, History, Settings. One activity,
programmatic layout, no pictures in v1.

## Verification evidence (build tier only, not phone-tested)

- File: Search-AI-v1.apk, 33,176 bytes, package com.sobhan.searchai,
  versionCode 1, versionName 1.0.
- apksigner verify: exit 0, APK Signature Scheme v2 and v3 true, 1 signer.
- aapt2 dump badging: package com.sobhan.searchai, sdkVersion 24,
  targetSdkVersion 34, INTERNET permission only, label Search AI,
  launchable activity com.sobhan.searchai.MainActivity.
- Dex scan: 0 matches for the banned strings, case-insensitive.
- Source scan: no credential prompts, banners, or credential buttons.
- Separation: the vocab APK (com.sobhan.vocabbuilder, 8,468,043 bytes) is
  untouched, and the different package means installing Search AI never
  overwrites it or its data.
- Build warnings: 4 non-fatal javac warnings, including a deprecated API
  note. No build errors.

## Known limits

- Not phone-tested. Install, tap-through, scroll, and the network
  translation have only build-tier evidence. The phone test decides.
- The machine translation uses an unofficial no-credential endpoint and
  reads only its first segment, so long phrases may translate partially
  and the endpoint may change without warning.
- Saved words and history live only on the phone in this app's own store.
  They do not sync with the web app, and the web app's saved words do not
  appear here.
- Rotation is handled by keeping the activity alive on configuration
  change, but that path is also untested on a phone.

## What v1 does NOT do yet

- No live AI answers. The Settings provider choice is stored for a later
  version; v1 never calls an AI API and never asks for a credential.
- No pictures in the Show card. The card is text only.
- Only the main definition and main example are shown. Extra examples,
  overview, background, and etymology are not bundled yet.
- No another-meaning swap. Words with several meanings show only the
  main one.
- History has Clear only, no per-item remove.
- FA to EN direction has no offline path and no Save. It is network
  translation only.
- No automated tests, no Play Store publishing route, no web wrapper:
  this is a standalone native app by design.

## Questions for Claude (improvement round)

1. The result view and the Show card share one render method, and Back
   re-renders the result to refresh Save and Mark learned states. Is there
   a cleaner way to keep those states in sync without re-rendering on Back?
2. The translation parser is hand-rolled and reads only the first segment
   of an unofficial endpoint response. How would you make it robust for
   multi-segment translations, empty responses, and endpoint changes?
3. Saved words, history, and learned words are newline-joined strings in
   SharedPreferences. What breaks first as those lists grow, and what
   storage would you switch to, in what order?
4. There are no automated tests and no phone test yet. What would you
   test first, and how, given the app is built with javac, aapt2, and R8
   and has no test harness?
5. The FA to EN direction has no offline path and no Save. How would you
   add offline Persian to English lookup from the bundled data without
   bloating the app, and what would you store for it?

## Build notes

- Working files: searchai-src (MainActivity.java, WordsData.java),
  AndroidManifest-searchai1.xml, res-searchai, build-searchai1.sh, all
  under the android-app directory. Per-version backup: searchai1-backup-src.
- WordsData.java is generated from words.json by gen-searchai-words.py;
  the generator is not part of the pushed source because the pushed
  source already contains the generated file.
- Signing: the working build script reads the keystore password at build
  time from the existing local vocab build script and never writes it
  into a new file. The repository copy of the build script (build.sh in
  the staged source directory) differs deliberately: it takes the
  password from an environment variable and uses repository-relative
  paths, so it runs without any local file the repo does not contain.
- The staged source directory contains source, manifest, resources, the
  repository build script, and this review. It contains no keystore, no
  password, and no APK binary.
