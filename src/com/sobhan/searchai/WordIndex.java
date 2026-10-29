package com.sobhan.searchai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Android-free word lookup logic, unit-testable on the JVM.

    All lookups run against WordsData.WORDS and the generated reverse
    index WordsData.FA_KEYS / WordsData.FA_WORD_IDX. */
public class WordIndex {

  /** True when the text contains any Arabic-script character. */
  public static boolean isPersian(String s) {
    if (s == null) return false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c >= '\u0600' && c <= '\u06FF') return true;
    }
    return false;
  }

  /** Normalize a Persian string for reverse-index lookup.

      Must match gen-searchai-words.py normalize_fa exactly:
      Arabic yeh/kaf become Persian yeh/kaf; ZWNJ, ZWJ, tatweel and
      harakat are stripped; spaces are stripped; Persian and
      Arabic-Indic digits become ASCII digits. */
  public static String normalizeFa(String s) {
    if (s == null) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\u064A') sb.append('\u06CC');
      else if (c == '\u0643') sb.append('\u06A9');
      else if (c == '\u200C' || c == '\u200D' || c == '\u0640') {
        // stripped
      } else if (c >= '\u064B' && c <= '\u0652') {
        // harakat, stripped
      } else if (c == ' ') {
        // stripped
      } else if (c >= '\u06F0' && c <= '\u06F9') {
        sb.append((char) ('0' + (c - '\u06F0')));
      } else if (c >= '\u0660' && c <= '\u0669') {
        sb.append((char) ('0' + (c - '\u0660')));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  public static WordsData.Word findExact(String q) {
    if (q == null) return null;
    String query = q.trim();
    if (query.length() == 0) return null;
    for (int i = 0; i < WordsData.WORDS.length; i++) {
      if (WordsData.WORDS[i].word.equalsIgnoreCase(query)) {
        return WordsData.WORDS[i];
      }
    }
    return null;
  }

  /** Similar words: substring either way, or edit distance 2 or less. */
  public static List<WordsData.Word> findSimilar(String q, int limit) {
    List<WordsData.Word> out = new ArrayList<WordsData.Word>();
    if (q == null) return out;
    String query = q.trim().toLowerCase();
    if (query.length() == 0) return out;
    for (int i = 0; i < WordsData.WORDS.length; i++) {
      if (out.size() >= limit) break;
      String wl = WordsData.WORDS[i].word.toLowerCase();
      if (wl.contains(query) || query.contains(wl)
          || levenshtein(wl, query) <= 2) {
        out.add(WordsData.WORDS[i]);
      }
    }
    return out;
  }

  public static int levenshtein(String a, String b) {
    int[] prev = new int[b.length() + 1];
    int[] cur = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) prev[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      cur[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        int del = prev[j] + 1;
        int ins = cur[j - 1] + 1;
        int sub = prev[j - 1] + cost;
        int best = del < ins ? del : ins;
        cur[j] = best < sub ? best : sub;
      }
      int[] tmp = prev;
      prev = cur;
      cur = tmp;
    }
    return prev[b.length()];
  }

  /** Persian-to-English lookup over the generated reverse index.

      Exact normalized matches first; if none, prefix matches. Shared
      keys return every matching word (a disambiguation list). */
  public static List<WordsData.Word> findByFa(String q, int limit) {
    List<WordsData.Word> out = new ArrayList<WordsData.Word>();
    if (q == null) return out;
    String norm = normalizeFa(q.trim());
    if (norm.length() == 0) return out;
    String[] keys = WordsData.FA_KEYS;
    int[] idx = WordsData.FA_WORD_IDX;
    int lo = 0;
    int hi = keys.length;
    while (lo < hi) {
      int mid = (lo + hi) / 2;
      if (keys[mid].compareTo(norm) < 0) lo = mid + 1;
      else hi = mid;
    }
    Set<Integer> seen = new LinkedHashSet<Integer>();
    for (int i = lo; i < keys.length && keys[i].equals(norm); i++) {
      seen.add(Integer.valueOf(idx[i]));
    }
    if (seen.isEmpty()) {
      for (int i = lo; i < keys.length && keys[i].startsWith(norm); i++) {
        seen.add(Integer.valueOf(idx[i]));
        if (seen.size() >= limit) break;
      }
    }
    for (Integer wi : seen) {
      out.add(WordsData.WORDS[wi.intValue()]);
      if (out.size() >= limit) break;
    }
    return out;
  }

  /** Verified CEFR levels, from user-provided material only.

      The web home screenshot gives five: Favoritism C1,
      Interpretation B2, Helpful A2, Transcendence C2, Eloquent C1.
      The web result screenshot for "yelling" gives a sixth: B1.
      Every other word returns null (no badge), never an invented level. */
  public static String cefrFor(String word) {
    if (word == null) return null;
    String w = word.trim().toLowerCase();
    if (w.equals("favoritism")) return "C1";
    if (w.equals("interpretation")) return "B2";
    if (w.equals("helpful")) return "A2";
    if (w.equals("transcendence")) return "C2";
    if (w.equals("eloquent")) return "C1";
    if (w.equals("yelling")) return "B1";
    return null;
  }
}
