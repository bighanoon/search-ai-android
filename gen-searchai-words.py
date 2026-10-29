#!/usr/bin/env python3
"""Generate WordsData.java for the Search AI APK from words.json.

Reads ~/workspace/apk-v4-spec/words.json (the deck word entries) and writes
a Java source file with only the fields the Search AI APK shows: word,
part of speech, phonetic, definition, Persian definition, example, Persian
example, synonyms, and antonyms. No invented content: every string comes
from words.json verbatim.

It also emits a Persian-to-English reverse index (FA_KEYS / FA_WORD_IDX):
gloss keys split from definitionFa only (never exampleFa, which would add
stopword noise), normalized the same way WordIndex.normalizeFa does in
Java, sorted, as parallel arrays. Shared keys appear as multiple rows.
Usage: gen-searchai-words.py [output-path]
Default output: ~/android-app/searchai-src/com/sobhan/searchai/WordsData.java
"""
import json
import os
import sys

SRC = os.path.expanduser("~/workspace/apk-v4-spec/words.json")
DEFAULT_OUT = os.path.expanduser(
    "~/android-app/searchai-src/com/sobhan/searchai/WordsData.java")


def java_str(s):
    if s is None:
        return '""'
    # json.dumps with ensure_ascii=True produces double-quoted output whose
    # escapes (\", \\, \n, \uXXXX) are all valid Java string escapes.
    return json.dumps(str(s), ensure_ascii=True)


def normalize_fa(s):
    """Mirror of WordIndex.normalizeFa in Java. Keep both in sync."""
    out = []
    for ch in s:
        o = ord(ch)
        if ch == "ي":
            out.append("ی")
        elif ch == "ك":
            out.append("ک")
        elif o in (0x200C, 0x200D, 0x0640):
            continue
        elif 0x064B <= o <= 0x0652:
            continue
        elif ch == " ":
            continue
        elif 0x06F0 <= o <= 0x06F9:
            out.append(chr(ord("0") + o - 0x06F0))
        elif 0x0660 <= o <= 0x0669:
            out.append(chr(ord("0") + o - 0x0660))
        else:
            out.append(ch)
    return "".join(out)


def fa_keys_for(definition_fa):
    """Split a Persian definition into gloss keys, then normalize."""
    keys = []
    if not definition_fa:
        return keys
    for part in definition_fa.replace("،", "؛").split("؛"):
        for seg in part.split(" و "):
            seg = seg.strip().rstrip(".؛،")
            if not seg:
                continue
            norm = normalize_fa(seg)
            if norm:
                keys.append(norm)
    return keys


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT
    with open(SRC, encoding="utf-8") as f:
        data = json.load(f)
    words = data["words"]
    os.makedirs(os.path.dirname(out_path), exist_ok=True)

    pairs = []  # (normalized key, word index)
    for idx, w in enumerate(words):
        for key in fa_keys_for(w.get("definitionFa", "")):
            pairs.append((key, idx))
    pairs.sort(key=lambda p: (p[0], p[1]))

    lines = []
    lines.append("package com.sobhan.searchai;")
    lines.append("")
    lines.append("public class WordsData {")
    lines.append("  public static class Word {")
    lines.append("    public String word;")
    lines.append("    public String pos;")
    lines.append("    public String phonetic;")
    lines.append("    public String definition;")
    lines.append("    public String definitionFa;")
    lines.append("    public String example;")
    lines.append("    public String exampleFa;")
    lines.append("    public String synonyms;")
    lines.append("    public String antonyms;")
    lines.append("")
    lines.append("    public Word(String word, String pos, String phonetic,")
    lines.append("        String definition, String definitionFa,")
    lines.append("        String example, String exampleFa,")
    lines.append("        String synonyms, String antonyms) {")
    lines.append("      this.word = word;")
    lines.append("      this.pos = pos;")
    lines.append("      this.phonetic = phonetic;")
    lines.append("      this.definition = definition;")
    lines.append("      this.definitionFa = definitionFa;")
    lines.append("      this.example = example;")
    lines.append("      this.exampleFa = exampleFa;")
    lines.append("      this.synonyms = synonyms;")
    lines.append("      this.antonyms = antonyms;")
    lines.append("    }")
    lines.append("  }")
    lines.append("")
    lines.append("  public static final Word[] WORDS = {")
    for w in words:
        fields = [
            java_str(w.get("word", "")),
            java_str(w.get("partOfSpeech", "")),
            java_str(w.get("phonetic", "")),
            java_str(w.get("definition", "")),
            java_str(w.get("definitionFa", "")),
            java_str(w.get("example", "")),
            java_str(w.get("exampleFa", "")),
            java_str(w.get("synonyms", "")),
            java_str(w.get("antonyms", "")),
        ]
        lines.append("    new Word(" + ", ".join(fields) + "),")
    lines.append("  };")
    lines.append("")
    lines.append("  // Persian-to-English reverse index, from definitionFa only.")
    lines.append("  // FA_KEYS is sorted; FA_WORD_IDX is the parallel word index.")
    lines.append("  public static final String[] FA_KEYS = {")
    for key, _idx in pairs:
        lines.append("    " + java_str(key) + ",")
    lines.append("  };")
    lines.append("")
    lines.append("  public static final int[] FA_WORD_IDX = {")
    for _key, idx in pairs:
        lines.append("    " + str(idx) + ",")
    lines.append("  };")
    lines.append("}")
    lines.append("")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("wrote %s (%d words, %d fa keys)" % (out_path, len(words), len(pairs)))


if __name__ == "__main__":
    main()
