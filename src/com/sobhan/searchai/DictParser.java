package com.sobhan.searchai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Android-free parsing for the keyless dictionary entry source
    (dictionaryapi.dev response shape), plus JSON round-trip for the
    local cache. DictParser never touches Android classes, so it can
    be unit-tested on the JVM against the real org.json jar.

    Never invents content: parse() returns null unless the response
    carries at least one non-empty definition. Anything the source
    does not provide (a section, a synonym, an example) is simply
    absent from the entry, and the UI says so instead of filling it.

    v4: the source returns one JSON entry per spelling variant (for
    example "rebel" arrives as a noun entry plus a verb entry). v3
    read only the first entry, which dropped the verb meaning and
    its synonyms ("defy") and antonyms ("obey", "submit"). v4 merges
    every entry, and each definition keeps its own part of speech. */
public class DictParser {

  public static class Def {
    public String definition;
    public String example;
    public String pos;
    public Def(String definition, String example, String pos) {
      this.definition = definition;
      this.example = example;
      this.pos = pos;
    }
  }

  public static class DictEntry {
    public String word;
    public String phonetic;
    public String pos;
    public List<Def> defs;
    public List<String> synonyms;
    public List<String> antonyms;
    public DictEntry(String word, String phonetic, String pos,
        List<Def> defs, List<String> synonyms, List<String> antonyms) {
      this.word = word;
      this.phonetic = phonetic;
      this.pos = pos;
      this.defs = defs;
      this.synonyms = synonyms;
      this.antonyms = antonyms;
    }
  }

  // Card bounds: enough for a full entry, small enough to cache safely.
  private static final int MAX_DEFS = 8;
  private static final int MAX_WORDS = 12;

  private static void addWords(List<String> out, LinkedHashSet<String> seen,
      JSONArray arr) {
    if (arr == null) return;
    for (int i = 0; i < arr.length() && out.size() < MAX_WORDS; i++) {
      Object o = arr.opt(i);
      if (o == null || o == JSONObject.NULL) continue;
      String s = o.toString().trim();
      if (s.length() == 0 || seen.contains(s)) continue;
      seen.add(s);
      out.add(s);
    }
  }

  private static String entryPhonetic(JSONObject entry) {
    String phonetic = entry.optString("phonetic", "").trim();
    if (phonetic.length() > 0) return phonetic;
    JSONArray phonetics = entry.optJSONArray("phonetics");
    if (phonetics != null) {
      for (int i = 0; i < phonetics.length(); i++) {
        JSONObject p = phonetics.optJSONObject(i);
        if (p == null) continue;
        String t = p.optString("text", "").trim();
        if (t.length() > 0) return t;
      }
    }
    return "";
  }

  /** Parse one dictionary response into a full entry, or null. */
  public static DictEntry parse(String resp) {
    if (resp == null) return null;
    try {
      JSONArray root = new JSONArray(resp);
      if (root.length() == 0) return null;
      String word = "";
      String phonetic = "";
      String pos = "";
      List<Def> defs = new ArrayList<Def>();
      List<String> synonyms = new ArrayList<String>();
      List<String> antonyms = new ArrayList<String>();
      LinkedHashSet<String> seenSyn = new LinkedHashSet<String>();
      LinkedHashSet<String> seenAnt = new LinkedHashSet<String>();
      for (int e = 0; e < root.length(); e++) {
        JSONObject entry = root.optJSONObject(e);
        if (entry == null) continue;
        if (word.length() == 0) {
          word = entry.optString("word", "").trim();
        }
        if (phonetic.length() == 0) {
          phonetic = entryPhonetic(entry);
        }
        JSONArray meanings = entry.optJSONArray("meanings");
        if (meanings == null) continue;
        for (int m = 0; m < meanings.length(); m++) {
          JSONObject meaning = meanings.optJSONObject(m);
          if (meaning == null) continue;
          String meaningPos = meaning.optString("partOfSpeech", "").trim();
          if (pos.length() == 0) pos = meaningPos;
          addWords(synonyms, seenSyn, meaning.optJSONArray("synonyms"));
          addWords(antonyms, seenAnt, meaning.optJSONArray("antonyms"));
          JSONArray ds = meaning.optJSONArray("definitions");
          if (ds == null) continue;
          for (int d = 0; d < ds.length() && defs.size() < MAX_DEFS; d++) {
            JSONObject defObj = ds.optJSONObject(d);
            if (defObj == null) continue;
            String defText = defObj.optString("definition", "").trim();
            if (defText.length() == 0) continue;
            String ex = defObj.optString("example", "").trim();
            defs.add(new Def(defText, ex, meaningPos));
            addWords(synonyms, seenSyn, defObj.optJSONArray("synonyms"));
            addWords(antonyms, seenAnt, defObj.optJSONArray("antonyms"));
          }
        }
      }
      if (word.length() == 0 || defs.isEmpty()) return null;
      return new DictEntry(word, phonetic, pos, defs, synonyms, antonyms);
    } catch (JSONException e) {
      return null;
    }
  }

  /** Serialize an entry for the local cache (dict::<word>). */
  public static String toJson(DictEntry e) {
    try {
      JSONObject o = new JSONObject();
      o.put("word", e.word);
      o.put("phonetic", e.phonetic);
      o.put("pos", e.pos);
      JSONArray ds = new JSONArray();
      for (int i = 0; i < e.defs.size(); i++) {
        JSONObject d = new JSONObject();
        d.put("d", e.defs.get(i).definition);
        d.put("e", e.defs.get(i).example);
        d.put("p", e.defs.get(i).pos);
        ds.put(d);
      }
      o.put("defs", ds);
      JSONArray syn = new JSONArray();
      for (int i = 0; i < e.synonyms.size(); i++) syn.put(e.synonyms.get(i));
      o.put("syn", syn);
      JSONArray ant = new JSONArray();
      for (int i = 0; i < e.antonyms.size(); i++) ant.put(e.antonyms.get(i));
      o.put("ant", ant);
      return o.toString();
    } catch (JSONException ex) {
      return null;
    }
  }

  /** Restore a cached entry, or null when the stored text is unusable. */
  public static DictEntry fromJson(String json) {
    if (json == null) return null;
    try {
      JSONObject o = new JSONObject(json);
      String word = o.optString("word", "").trim();
      if (word.length() == 0) return null;
      String entryPos = o.optString("pos", "");
      List<Def> defs = new ArrayList<Def>();
      JSONArray ds = o.optJSONArray("defs");
      if (ds != null) {
        for (int i = 0; i < ds.length(); i++) {
          JSONObject d = ds.optJSONObject(i);
          if (d == null) continue;
          String defText = d.optString("d", "").trim();
          if (defText.length() == 0) continue;
          // Entries cached before v4 carry no per-definition pos;
          // they fall back to the entry pos, never to an invented one.
          String defPos = d.optString("p", entryPos);
          defs.add(new Def(defText, d.optString("e", ""), defPos));
        }
      }
      if (defs.isEmpty()) return null;
      List<String> synonyms = new ArrayList<String>();
      JSONArray syn = o.optJSONArray("syn");
      if (syn != null) {
        for (int i = 0; i < syn.length(); i++) {
          Object v = syn.opt(i);
          if (v != null && v != JSONObject.NULL) synonyms.add(v.toString());
        }
      }
      List<String> antonyms = new ArrayList<String>();
      JSONArray ant = o.optJSONArray("ant");
      if (ant != null) {
        for (int i = 0; i < ant.length(); i++) {
          Object v = ant.opt(i);
          if (v != null && v != JSONObject.NULL) antonyms.add(v.toString());
        }
      }
      return new DictEntry(word, o.optString("phonetic", ""),
          entryPos, defs, synonyms, antonyms);
    } catch (JSONException e) {
      return null;
    }
  }
}
