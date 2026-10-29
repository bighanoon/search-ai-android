package com.sobhan.searchai;

import java.util.ArrayList;
import java.util.List;

/** Android-free newline-joined list encoding for SharedPreferences. */
public class ListCodec {

  public static String encode(List<String> list) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      String s = list.get(i);
      if (s == null || s.length() == 0) continue;
      if (sb.length() > 0) sb.append("\n");
      sb.append(s);
    }
    return sb.toString();
  }

  public static List<String> decode(String raw) {
    List<String> out = new ArrayList<String>();
    if (raw == null || raw.length() == 0) return out;
    String[] parts = raw.split("\n");
    for (int i = 0; i < parts.length; i++) {
      if (parts[i].length() > 0) out.add(parts[i]);
    }
    return out;
  }

  public static boolean containsIgnoreCase(List<String> list, String v) {
    if (v == null) return false;
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i).equalsIgnoreCase(v)) return true;
    }
    return false;
  }
}
