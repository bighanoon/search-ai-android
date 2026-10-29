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

  /** True when at least one section carries text. */
  public static boolean hasAny(Sections s) {
    return s != null
        && (s.overview != null || s.background != null
            || s.hook != null || s.answer != null);
  }
}
