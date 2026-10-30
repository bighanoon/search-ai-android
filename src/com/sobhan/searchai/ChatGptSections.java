package com.sobhan.searchai;

import org.json.JSONObject;

/** Android-free parser for the ChatGPT vocabulary sections.

    The API is called with JSON mode, so the message content is itself
    a JSON object with optional string fields: overview, background,
    hook, answer. Rules, matching the web app's validation:
    - Unparseable JSON, or a top level that is not an object, returns
      null: the caller falls back to the dictionary card, never a
      fake entry.
    - A field that is missing, empty, or the wrong type is omitted
      (null) and never invented; the other fields still parse.
    - A valid object with no usable fields parses to a Sections whose
      fields are all null: the caller shows nothing for it. */
public class ChatGptSections {

  public static class Sections {
    public String overview;
    public String background;
    public String hook;
    public String answer;
    // v7 How Common: label plus 1-7 level, both optional. A level
    // outside 1-7 parses to -1, which means "omit the meter".
    public String frequencyLabel;
    public int frequencyLevel = -1;
  }

  public static Sections parse(String json) {
    if (json == null) return null;
    try {
      JSONObject obj = new JSONObject(json);
      Sections s = new Sections();
      s.overview = optText(obj, "overview");
      s.background = optText(obj, "background");
      s.hook = optText(obj, "hook");
      s.answer = optText(obj, "answer");
      s.frequencyLabel = optText(obj, "frequencyLabel");
      s.frequencyLevel = optLevel(obj, "frequencyLevel");
      return s;
    } catch (Exception e) {
      return null;
    }
  }

  private static String optText(JSONObject obj, String key) {
    try {
      Object v = obj.opt(key);
      if (!(v instanceof String)) return null;
      String t = ((String) v).trim();
      return t.length() == 0 ? null : t;
    } catch (Exception e) {
      return null;
    }
  }

  /** Integer field clamped to the 1-7 frequency scale; anything
      missing, non-numeric, or out of range parses to -1. */
  private static int optLevel(JSONObject obj, String key) {
    try {
      Object v = obj.opt(key);
      if (v instanceof Number) {
        int n = ((Number) v).intValue();
        return (n >= 1 && n <= 7) ? n : -1;
      }
      return -1;
    } catch (Exception e) {
      return -1;
    }
  }

  /** True when at least one section carries text. */
  public static boolean hasAny(Sections s) {
    return s != null
        && (s.overview != null || s.background != null
            || s.hook != null || s.answer != null);
  }
}
