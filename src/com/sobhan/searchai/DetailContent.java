package com.sobhan.searchai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Android-free helpers for the v7 Show card and Tell Me More detail.

    Everything here is pure text logic, so it is covered by JVM tests:
    where the word is bolded inside a sentence, which examples and
    related words the detail shows, and when the How Common meter may
    appear. No content is ever invented here: a helper returns empty
    or -1 when its input has nothing real, and the caller omits the
    section instead of guessing. */
public class DetailContent {

  /** One half-open [start, end) range inside a string. */
  public static class Range {
    public final int start;
    public final int end;
    Range(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  /** Case-insensitive, non-overlapping occurrences of word in text.
      Returns empty when either side is blank. A multi-word phrase
      matches as one phrase. */
  public static List<Range> boldRanges(String text, String word) {
    List<Range> out = new ArrayList<Range>();
    if (text == null || word == null) return out;
    String needle = word.trim().toLowerCase(Locale.US);
    if (needle.length() == 0) return out;
    String hay = text.toLowerCase(Locale.US);
    int from = 0;
    while (true) {
      int at = hay.indexOf(needle, from);
      if (at < 0) break;
      out.add(new Range(at, at + needle.length()));
      from = at + needle.length();
    }
    return out;
  }

  /** Examples in order, duplicates and blanks removed. */
  public static List<String> collectExamples(List<String> examples) {
    List<String> out = new ArrayList<String>();
    Set<String> seen = new HashSet<String>();
    if (examples == null) return out;
    for (int i = 0; i < examples.size(); i++) {
      String e = examples.get(i);
      if (e == null) continue;
      String t = e.trim();
      if (t.length() == 0) continue;
      String key = t.toLowerCase(Locale.US);
      if (seen.contains(key)) continue;
      seen.add(key);
      out.add(t);
    }
    return out;
  }

  /** Related words: synonyms first, then similar words, duplicates
      removed case-insensitively, first spelling kept, capped at 10. */
  public static List<String> relatedWords(List<String> synonyms,
      List<String> similar) {
    List<String> out = new ArrayList<String>();
    Set<String> seen = new HashSet<String>();
    addRelated(out, seen, synonyms);
    addRelated(out, seen, similar);
    return out;
  }

  private static void addRelated(List<String> out, Set<String> seen,
      List<String> words) {
    if (words == null) return;
    for (int i = 0; i < words.size(); i++) {
      if (out.size() >= 10) return;
      String w = words.get(i);
      if (w == null) continue;
      String t = w.trim();
      if (t.length() == 0) continue;
      String key = t.toLowerCase(Locale.US);
      if (seen.contains(key)) continue;
      seen.add(key);
      out.add(t);
    }
  }

  /** The examples the In Action card shows before "Show more". */
  public static List<String> firstExamples(List<String> all) {
    List<String> out = new ArrayList<String>();
    if (all == null) return out;
    for (int i = 0; i < all.size() && i < 3; i++) out.add(all.get(i));
    return out;
  }

  /** The examples "Show more" reveals, empty when there are none. */
  public static List<String> restExamples(List<String> all) {
    List<String> out = new ArrayList<String>();
    if (all == null) return out;
    for (int i = 3; i < all.size(); i++) out.add(all.get(i));
    return out;
  }

  /** The 1-7 frequency scale used by the How Common meter, matching
      the ChatGPT prompt: 1 Very rare, 2 Rare, 3 Uncommon,
      4 Fairly common, 5 Common, 6 Very common, 7 Extremely common.
      Unknown labels map to -1, which means "omit the meter". */
  public static int frequencyLevelForLabel(String label) {
    if (label == null) return -1;
    String l = label.trim().toLowerCase(Locale.US);
    if (l.equals("very rare")) return 1;
    if (l.equals("rare")) return 2;
    if (l.equals("uncommon")) return 3;
    if (l.equals("fairly common")) return 4;
    if (l.equals("common")) return 5;
    if (l.equals("very common")) return 6;
    if (l.equals("extremely common")) return 7;
    return -1;
  }

  /** Label for a valid level, null otherwise. */
  public static String frequencyLabelForLevel(int level) {
    switch (level) {
      case 1: return "Very rare";
      case 2: return "Rare";
      case 3: return "Uncommon";
      case 4: return "Fairly common";
      case 5: return "Common";
      case 6: return "Very common";
      case 7: return "Extremely common";
      default: return null;
    }
  }

  /** True only for levels 1-7; anything else omits the meter. */
  public static boolean validFrequencyLevel(int level) {
    return level >= 1 && level <= 7;
  }
}
