#!/bin/bash
# Search AI APK v4 build (repository copy).
# This script differs deliberately from the working build script: it uses
# repository-relative paths and takes the keystore password from an
# environment variable, so it runs without any local file the repo does
# not contain. The password is never stored in this file.
#
# Toolchain: JDK 21 and the Android SDK (build-tools 34.0.0, platform
# android-34). Adjust JDK and SDK paths below if yours differ.
# R8: fetch the stable R8 jar from Google Maven (release 9.6.0-alpha08)
# and point R8_JAR at it. Do not use the bundled build-tools d8: it
# crashes on this app's anonymous classes.
# Signing: point KS at your own debug keystore and KS_ALIAS at its alias,
# then set APK_KS_PASS to the keystore password before running.
# v3 on top of v2: web home shape, keyless dictionary full entries with
# local cache, CEFR badges only where verified, on-device speaker.
set -euo pipefail
export PATH="$HOME/jdk21/bin:$PATH"
SDK="$HOME/android-sdk"
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-34/android.jar"
R8_JAR="$HOME/android-app/r8.jar"
KS="$HOME/android-app/debug.keystore"
KS_ALIAS="vocabkey"
: "${APK_KS_PASS:?Set APK_KS_PASS to the debug keystore password before running.}"

echo "== aapt2 compile =="
"$BT/aapt2" compile -o compiled.zip res/values/strings.xml

echo "== aapt2 link =="
"$BT/aapt2" link -o app-unsigned.apk -I "$AJ" --manifest AndroidManifest.xml compiled.zip

echo "== javac =="
rm -rf obj dex
mkdir -p obj dex
javac -Xlint:-options -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -d obj \
  src/com/sobhan/searchai/*.java

echo "== R8 dex =="
java -cp "$R8_JAR" com.android.tools.r8.D8 --release --lib "$AJ" --min-api 24 \
  --output dex obj/com/sobhan/searchai/*.class

echo "== add dex, align, sign =="
cd dex && jar uf ../app-unsigned.apk classes.dex && cd ..
"$BT/zipalign" -f 4 app-unsigned.apk app-aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "env:APK_KS_PASS" --key-pass "env:APK_KS_PASS" \
  --out ./Search-AI-v4.apk app-aligned.apk

echo "== verify =="
"$BT/apksigner" verify ./Search-AI-v4.apk && echo "SIGNATURE OK"
"$BT/aapt2" dump badging ./Search-AI-v4.apk | head -4
ls -la ./Search-AI-v4.apk
