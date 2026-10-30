# Search AI v10 — review notes

Base: v9 staged source, same app "Search AI", package
`com.sobhan.searchai`, same debug keystore, same `searchai` store, so
v10 installs over v9 and keeps saved words and history.
versionCode 10, versionName 10.0, minSdk 24, targetSdk 34, INTERNET only.

## What changed (user-confirmed, batched)

1. Result page only: while a word result is shown, the tip line, the
   History pill, the Settings pill, and the Updating cards pill are
   hidden. With no result shown (home page), they show exactly as in
   v9. My Words and Try a word stay hidden on a result page (v8).
   Recent searches stay on both pages. The Updating cards download
   function itself is untouched and stays available from home.
2. The small X close control is removed entirely. The result block
   starts directly with the tabs row. System Back on a result clears
   the result and restores the home sections (tip line, pills,
   Updating cards, My Words, Try a word) instead of exiting the app,
   so the result page is never left without a way back to home.
   (Back order: Tell Me More sheet first, then the Show card popup,
   then clear-the-result, then the back stack, then the search screen.)
3. Show card: a centered popup over a dimmed backdrop (tap the
   backdrop to close), not a full-height sheet and not stuck to the
   bottom. The picture panel stays on top when the word has a
   picture (bundled or downloaded); a word with no picture gets no
   panel, never a fake picture. Tell Me More is compact, about the
   size of its text, pushed to the right of the add/heart/share
   icons (its gradient border is kept). The rest of the card is as
   in v7: word + speaker + "..." menu (Copy/Share only), one
   definition + small FA link, one example inset + FA link,
   full-width dark Close below. The Tell Me More bottom sheet is
   unchanged.

## What did not change

Saved-word data, downloaded pictures, the Settings-only ChatGPT
option, the bulk "Download all words" file, the select-to-translate
popup, FA small text links, 38 bundled pictures, "by SobhaN", and
zero banned strings (banned-brand scan: 0 in dex).

## Verification (build tier only — phone test still required)

- JVM tests: 61 pass (54 carried from v9, with the two v9
  close-control tests updated to the v10 rule, plus 7 new v10 tests
  for the extras rule, Back-clears-result, centered card, picture
  on top, compact Tell Me More, and no-saved-data-touch).
- `verify.sh` gate passed: apksigner verify OK, cert SHA-256 matches
  v9 (`e1733f5112c8ac821d71e9b5cfa4593d9b33c74a7e2b3f9308fadfc88faf22fd`),
  badging package/version/SDK/permissions as above, dex banned
  strings 0, resources.arsc Stored, v1–v9 APKs untouched, 38
  drawables bundled, v10-added saved-data lines 0.
- Not phone-tested: the result-page hiding, Back-clears-result,
  centered popup, picture-on-top, and compact Tell Me More all need
  the user's phone check before they count as confirmed.

## Files

- `build.sh` is the repository copy: relative paths, keystore
  password from the `APK_KS_PASS` environment variable only. No
  keystore, no password, and no APK are stored in this directory.
- `verify.sh` is the build gate (same script as the working copy).
