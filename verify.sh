#!/bin/bash
# Build gate for Search AI v10: signature + certificate match, badging,
# banned-string scan, and the resources.arsc Stored check.
# Build tier only: it never proves anything on the user's phone.
set -euo pipefail
export PATH="$HOME/jdk21/bin:$PATH"
BT="$HOME/android-sdk/build-tools/34.0.0"
APK="$HOME/workspace/Search-AI-v10.apk"
V1="$HOME/workspace/Search-AI-v9.apk"
VOCAB="$HOME/workspace/SAT-Vocab-Builder-v13.apk"

echo "== apksigner verify =="
"$BT/apksigner" verify --print-certs "$APK"

echo "== cert match v8 =="
C2="$("$BT/apksigner" verify --print-certs "$APK" | grep -i 'SHA-256' | head -1)"
C1="$("$BT/apksigner" verify --print-certs "$V1" | grep -i 'SHA-256' | head -1)"
if [ "$C2" = "$C1" ]; then
  echo "CERT MATCHES V9"
else
  echo "CERT MISMATCH"; exit 1
fi

echo "== badging =="
"$BT/aapt2" dump badging "$APK" | head -6

echo "== dex banned strings =="
N="$(unzip -p "$APK" classes.dex | strings -a | grep -ic -e muse -e vividvocab || true)"
echo "banned matches: $N"
[ "$N" = "0" ] || exit 1

echo "== resources.arsc Stored =="
unzip -v "$APK" resources.arsc | grep -i 'Stored' || { echo "ARSC NOT STORED"; exit 1; }

echo "== vocab apk untouched =="
ls -la "$VOCAB"
echo "== v9 structural checks (carried) =="
SRC="$HOME/android-app/searchai10-src/src/com/sobhan/searchai"
# FA rows must not use the pill-bar container (was: LinearLayout pill = pill(CARD2);)
if grep -q 'LinearLayout pill = pill(CARD2);' "$SRC/MainActivity.java"; then echo "FA STILL USES PILL BAR"; exit 1; fi
# No per-word Word file button: no filePill on the result card, no fileBtn on saved rows
if grep -q 'filePill\|fileBtn' "$SRC/MainActivity.java"; then echo "PER-WORD WORD FILE BUTTON REMAINS"; exit 1; fi
# Bulk button and Updating cards option must exist
grep -q 'shareAllSavedWords\|Download all words' "$SRC/MainActivity.java" || { echo "BULK BUTTON MISSING"; exit 1; }
grep -q 'Updating cards' "$SRC/MainActivity.java" || { echo "UPDATING CARDS MISSING"; exit 1; }
# System toolbar suppression and single-line popup labels
grep -q 'setCustomSelectionActionModeCallback' "$SRC/SelTextView.java" || { echo "TOOLBAR SUPPRESSION MISSING"; exit 1; }
grep -q 'setSingleLine(true)' "$SRC/MainActivity.java" || { echo "POPUP SINGLELINE MISSING"; exit 1; }
# Show card picture resolution and bundled drawables
grep -q 'pictureBitmapFor' "$SRC/MainActivity.java" || { echo "SHOW CARD PICTURE MISSING"; exit 1; }
DRAWN="$(ls "$HOME/android-app/searchai10-src/res/drawable/"*.webp 2>/dev/null | wc -l)"
echo "bundled drawables: $DRAWN"
[ "$DRAWN" = "38" ] || { echo "DRAWABLE COUNT WRONG"; exit 1; }
# v7 redesign: no All-information list, caption, hook, or swap hint
# on the Show card; no inline result sections; detail is a sheet.
if grep -q 'All information' "$SRC/MainActivity.java"; then echo "ALL-INFORMATION LIST REMAINS"; exit 1; fi
if grep -q 'THE MEANING, IN ACTION' "$SRC/MainActivity.java"; then echo "SHOW CARD CAPTION REMAINS"; exit 1; fi
if grep -q 'tap the swap button' "$SRC/MainActivity.java"; then echo "SWAP HINT REMAINS"; exit 1; fi
if grep -q 'defSection\|exSection\|synSection\|simSection' "$SRC/MainActivity.java"; then echo "INLINE RESULT SECTIONS REMAIN"; exit 1; fi
if grep -q 'navigate("detail")' "$SRC/MainActivity.java"; then echo "DETAIL STILL A SCREEN"; exit 1; fi
grep -q 'buildDetailSheet\|renderDetailContent' "$SRC/MainActivity.java" || { echo "DETAIL SHEET MISSING"; exit 1; }
grep -q 'gradientBorderButton\|topRoundedBitmap\|cardStatus' "$SRC/MainActivity.java" || { echo "SHOW CARD REDESIGN MISSING"; exit 1; }
grep -q 'chatgpt7::' "$SRC/MainActivity.java" || { echo "V7 CHATGPT CACHE KEY MISSING"; exit 1; }
grep -q 'frequencyLevel' "$SRC/MainActivity.java" || { echo "FREQUENCY FIELD MISSING"; exit 1; }
echo "== v8 result-page sections (carried) =="
# The visibility rule lives in ResultPageSections and is wired into
# every result path; nothing else may show or hide these sections.
grep -q 'class ResultPageSections' "$SRC/ResultPageSections.java" || { echo "RESULT SECTIONS RULE MISSING"; exit 1; }
grep -q 'updateResultSections();' "$SRC/MainActivity.java" || { echo "UPDATE SECTIONS MISSING"; exit 1; }
for fn in renderResultInline showTranslationOnly showFaTranslation renderTranslationResult; do
  awk "/private .* $fn\(/,/^  }/" "$SRC/MainActivity.java" | grep -q 'updateResultSections();' || { echo "UPDATE MISSING IN $fn"; exit 1; }
done
# Back on a result (replacing the old X) must restore the home sections.
grep -A2 'lastResultData = null;' "$SRC/MainActivity.java" | grep -q 'updateResultSections();' || { echo "BACK DOES NOT RESTORE SECTIONS"; exit 1; }
# Visibility of these two sections is only ever set by the helper
# (plus the empty-input note override, which is not a word result).
NSET="$(grep -c 'myWordsBox.setVisibility\|trySection.setVisibility' "$SRC/MainActivity.java")"
echo "section visibility sets: $NSET"
[ "$NSET" = "4" ] || { echo "UNEXPECTED SECTION VISIBILITY SETS"; exit 1; }
# The v8 diff must not add any saved-data writes: no new savedSet,
# learnedSet, or prefs.edit lines versus v7.
DIFFNEW="$(diff ~/workspace/search-ai-apk-v9-source/src/com/sobhan/searchai/MainActivity.java "$SRC/MainActivity.java" | grep '^>' | grep -c 'savedSet\|learnedSet\|prefs\.edit' || true)"
echo "v10-added saved-data lines: $DIFFNEW"
[ "$DIFFNEW" = "0" ] || { echo "V10 TOUCHES SAVED DATA"; exit 1; }
echo "== v1-v9 untouched =="
[ "$(stat -c%s "$HOME/workspace/Search-AI-v1.apk")" = "33176" ] || { echo "V1 CHANGED"; exit 1; }
[ "$(stat -c%s "$HOME/workspace/Search-AI-v2.apk")" = "37273" ] || { echo "V2 CHANGED"; exit 1; }
[ "$(stat -c%s "$HOME/workspace/Search-AI-v3.apk")" = "45465" ] || { echo "V3 CHANGED"; exit 1; }
[ "$(stat -c%s "$HOME/workspace/Search-AI-v4.apk")" = "53657" ] || { echo "V4 CHANGED"; exit 1; }
[ "$(stat -c%s "$HOME/workspace/Search-AI-v5.apk")" = "70042" ] || { echo "V5 CHANGED"; exit 1; }
[ "$(stat -c%s "$HOME/workspace/Search-AI-v6.apk")" = "2735382" ] || { echo "V6 CHANGED"; exit 1; }
[ "$(stat -c%s "$HOME/workspace/Search-AI-v7.apk")" = "2743574" ] || { echo "V7 CHANGED"; exit 1; }
[ "$(stat -c%s "$HOME/workspace/Search-AI-v8.apk")" = "2743574" ] || { echo "V8 CHANGED"; exit 1; }
echo "== v10 result header and card =="
# The duplicate heading is gone (v9) and the X is gone too (v10):
# no headerRow method, no closeRow method or call site remains, and
# the result block starts directly with the tabs row.
if grep -q 'headerRow' "$SRC/MainActivity.java"; then echo "DUPLICATE HEADER REMAINS"; exit 1; fi
if grep -q 'closeRow' "$SRC/MainActivity.java"; then echo "X CLOSE ROW REMAINS"; exit 1; fi
grep -q 'class ResultHeader' "$SRC/ResultHeader.java" || { echo "RESULT HEADER RULE MISSING"; exit 1; }
grep -A2 'public static boolean hasCloseControl' "$SRC/ResultHeader.java" | grep -q 'return false' || { echo "X NOT REMOVED IN RULE"; exit 1; }
grep -A2 'public static boolean backClearsResult' "$SRC/ResultHeader.java" | grep -q 'return true' || { echo "BACK-CLEARS RULE MISSING"; exit 1; }
# Back on a result restores the home sections.
grep -A2 'lastResultData = null;' "$SRC/MainActivity.java" | grep -q 'updateResultSections();' || { echo "BACK DOES NOT RESTORE SECTIONS"; exit 1; }
# v10 extras rule: tip line, History/Settings pills, Updating cards
# hide on a result page and return on the home page.
grep -q 'class ResultPageSections' "$SRC/ResultPageSections.java" || { echo "RESULT SECTIONS RULE MISSING"; exit 1; }
grep -q 'extrasVisible' "$SRC/ResultPageSections.java" || { echo "EXTRAS RULE MISSING"; exit 1; }
grep -q 'extrasVisible' "$SRC/MainActivity.java" || { echo "EXTRAS NOT WIRED"; exit 1; }
for f in tipView homePillsRow updateRow; do
  grep -q "$f" "$SRC/MainActivity.java" || { echo "EXTRAS FIELD MISSING: $f"; exit 1; }
done
# v10 Show card: centered popup overlay (not a screen), picture on
# top, compact Tell Me More; no navigate("card") remains.
if grep -q 'navigate("card")' "$SRC/MainActivity.java"; then echo "CARD STILL A SCREEN"; exit 1; fi
grep -q 'buildCardOverlay\|hideCardPopup' "$SRC/MainActivity.java" || { echo "CARD POPUP MISSING"; exit 1; }
grep -q 'cardPopupBox' "$SRC/MainActivity.java" || { echo "CARD POPUP BOX MISSING"; exit 1; }
grep -q 'Gravity.CENTER' "$SRC/MainActivity.java" || { echo "CARD NOT CENTERED"; exit 1; }
grep -q 'tellParams\|tellSpacer' "$SRC/MainActivity.java" || { echo "COMPACT TELL ME MORE MISSING"; exit 1; }
if grep -q 'actions.addView(tellMore, new LinearLayout.LayoutParams(0,' "$SRC/MainActivity.java"; then echo "TELL ME MORE STILL FULL-WIDTH"; exit 1; fi
grep -q 'class ShowCardLayout' "$SRC/ShowCardLayout.java" || { echo "SHOW CARD LAYOUT RULE MISSING"; exit 1; }
# Everything else on the result page is still there: tabs, New word,
# TRANSLATION card, All definitions / Similar word buttons, word card
# buttons, Mark learned, archive note, Recent searches, Updating cards,
# and the History/Settings pills (home page).
for keep in 'Translation", "Definitions' 'New word' 'TRANSLATION' 'All definitions' 'Similar word' 'Show card' 'Mark learned' 'Updating cards' 'History' 'Settings'; do
  grep -q "$keep" "$SRC/MainActivity.java" || { echo "RESULT CONTENT MISSING: $keep"; exit 1; }
done
echo "GATE PASSED"
