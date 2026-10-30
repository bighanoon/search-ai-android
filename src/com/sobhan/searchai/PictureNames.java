package com.sobhan.searchai;

import java.util.Locale;

/** Android-free naming for word pictures, shared by the bundled
    drawables, the downloaded files, and the hosted pictures folder,
    so all three resolve the same word to the same file.

    Stem rule: lowercase, every run of non-letters/digits becomes one
    underscore, leading and trailing underscores trimmed. "A piece of
    cake" becomes "a_piece_of_cake"; "yelling" stays "yelling".
    Bundled drawables are named "pic_" + stem; downloaded and hosted
    files are named stem + ".webp". */
public class PictureNames {

  /** The stem for a word (no prefix, no extension). */
  public static String stem(String word) {
    if (word == null) return "";
    String lower = word.trim().toLowerCase(Locale.ROOT);
    StringBuilder sb = new StringBuilder();
    boolean lastUnder = true;
    for (int i = 0; i < lower.length(); i++) {
      char c = lower.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        sb.append(c);
        lastUnder = false;
      } else {
        if (!lastUnder) sb.append('_');
        lastUnder = true;
      }
    }
    String s = sb.toString();
    while (s.startsWith("_")) s = s.substring(1);
    while (s.endsWith("_")) s = s.substring(0, s.length() - 1);
    return s;
  }

  /** Bundled drawable resource name for a word ("pic_yelling"). */
  public static String drawableName(String word) {
    String s = stem(word);
    if (s.length() == 0) return "";
    return "pic_" + s;
  }

  /** Downloaded and hosted file name for a word ("yelling.webp"). */
  public static String fileName(String word) {
    String s = stem(word);
    if (s.length() == 0) return "";
    return s + ".webp";
  }
}
