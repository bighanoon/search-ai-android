package com.sobhan.searchai;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Android-free parsing for the hosted pictures manifest that the
    "Updating cards" option downloads. Manifest shape, fixed:
    {"pictures":[{"word":"yelling","file":"yelling.webp","version":1}]}
    A picture entry is usable only when word, file, and version are
    all present; anything else is skipped, never guessed. The file
    name is taken from the manifest, never built from the word, so a
    renamed hosted file still resolves. */
public class UpdatesManifest {

  public static class Picture {
    public String word;
    public String file;
    public int version;
    public Picture(String word, String file, int version) {
      this.word = word;
      this.file = file;
      this.version = version;
    }
  }

  /** Parse a manifest, or an empty list when it is unusable. */
  public static List<Picture> parse(String json) {
    List<Picture> out = new ArrayList<Picture>();
    if (json == null) return out;
    try {
      JSONObject o = new JSONObject(json);
      JSONArray arr = o.optJSONArray("pictures");
      if (arr == null) return out;
      for (int i = 0; i < arr.length(); i++) {
        JSONObject p = arr.optJSONObject(i);
        if (p == null) continue;
        String word = p.optString("word", "").trim();
        String file = p.optString("file", "").trim();
        int version = p.optInt("version", 0);
        if (word.length() == 0 || file.length() == 0 || version <= 0) {
          continue;
        }
        if (file.contains("/") || file.contains("\\")
            || file.equals(".") || file.equals("..")) {
          continue;
        }
        out.add(new Picture(word, file, version));
      }
    } catch (JSONException e) {
      return out;
    }
    return out;
  }
}
