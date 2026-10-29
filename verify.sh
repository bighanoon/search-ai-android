#!/bin/bash
# Build gate for Search AI v2: signature + certificate match, badging,
# banned-string scan, and the resources.arsc Stored check.
# Build tier only: it never proves anything on the user's phone.
set -euo pipefail
export PATH="$HOME/jdk21/bin:$PATH"
BT="$HOME/android-sdk/build-tools/34.0.0"
APK="$HOME/workspace/Search-AI-v2.apk"
V1="$HOME/workspace/Search-AI-v1.apk"
VOCAB="$HOME/workspace/SAT-Vocab-Builder-v13.apk"

echo "== apksigner verify =="
"$BT/apksigner" verify --print-certs "$APK"

echo "== cert match v1 =="
C2="$("$BT/apksigner" verify --print-certs "$APK" | grep -i 'SHA-256' | head -1)"
C1="$("$BT/apksigner" verify --print-certs "$V1" | grep -i 'SHA-256' | head -1)"
if [ "$C2" = "$C1" ]; then
  echo "CERT MATCHES V1"
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
echo "GATE PASSED"
