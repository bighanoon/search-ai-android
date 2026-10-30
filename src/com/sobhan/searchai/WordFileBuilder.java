package com.sobhan.searchai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** Android-free builder for the per-word JSON file the app can share.

    The file holds exactly what the app holds for the word, nothing
    invented: missing sections are omitted, never faked, and no picture
    is ever embedded (the picture is generated from this file later).
    Translation-only words carry the translation plus a plain note. */
public class WordFileBuilder {

  /** One definition with its example sentences. */
  public static class Def {
    public String partOfSpeech;
    public String definition;
    public String example;

    public Def(String partOfSpeech, String definition, String example) {
      this.partOfSpeech = partOfSpeech;
      this.definition = definition;
      this.example = example;
    }
  }

  /** Everything the app holds for one word. Null or empty means omit. */
  public static class Entry {
    public String word;
    public List<String> partsOfSpeech = new ArrayList<String>();
    public List<Def> definitions = new ArrayList<Def>();
    public List<String> synonyms = new ArrayList<String>();
    public List<String> antonyms = new ArrayList<String>();
    public String persian;
    public List<String> persianAlternatives = new ArrayList<String>();
    public String english;
    public String phonetic;
    public String memoryHook;
    public String scene;
    public String cefr;
    public String source;
    public String dateSearched;
    public String note;
  }

  /** File name for a word: lowercase, spaces become hyphens. */
  public static String filenameFor(String word) {
    if (word == null) word = "";
    String lower = word.trim().toLowerCase(Locale.ROOT);
    StringBuilder sb = new StringBuilder();
    boolean lastDash = true;
    for (int i = 0; i < lower.length(); i++) {
      char c = lower.charAt(i);
      if (c <= ' ' || c == '\t' || c == '\n' || c == '\r') {
        if (!lastDash) sb.append('-');
        lastDash = true;
      } else if (c == '/' || c == '\\' || c == ':' || c == '*'
          || c == '?' || c == '"' || c == '<' || c == '>'
          || c == '|' || c < 0x20) {
        // illegal in file names: dropped
      } else {
        sb.append(c);
        lastDash = false;
      }
    }
    String name = sb.toString();
    while (name.startsWith("-") || name.startsWith(".")) {
      name = name.substring(1);
    }
    while (name.endsWith("-") || name.endsWith(".")) {
      name = name.substring(0, name.length() - 1);
    }
    if (name.length() == 0) name = "word";
    return name + ".json";
  }

  /** Builds the file content. Field order is fixed for readability;
      consumers parse it as JSON, so order carries no meaning. */
  public static String buildJson(Entry e) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    boolean first = true;
    first = field(sb, first, "word", e.word);
    if (!e.partsOfSpeech.isEmpty()) {
      first = arrayField(sb, first, "partsOfSpeech", e.partsOfSpeech);
    }
    if (!e.definitions.isEmpty()) {
      if (!first) sb.append(",\n");
      first = false;
      sb.append("  \"definitions\": [\n");
      for (int i = 0; i < e.definitions.size(); i++) {
        Def d = e.definitions.get(i);
        sb.append("    {");
        boolean df = true;
        if (d.partOfSpeech != null && d.partOfSpeech.length() > 0) {
          sb.append("\"partOfSpeech\": ").append(JSONObject.quote(d.partOfSpeech));
          df = false;
        }
        if (d.definition != null && d.definition.length() > 0) {
          if (!df) sb.append(", ");
          sb.append("\"definition\": ").append(JSONObject.quote(d.definition));
          df = false;
        }
        if (d.example != null && d.example.length() > 0) {
          if (!df) sb.append(", ");
          sb.append("\"example\": ").append(JSONObject.quote(d.example));
        }
        sb.append("}");
        if (i < e.definitions.size() - 1) sb.append(",");
        sb.append("\n");
      }
      sb.append("  ]");
    }
    if (!e.synonyms.isEmpty()) {
      first = arrayField(sb, first, "synonyms", e.synonyms);
    }
    if (!e.antonyms.isEmpty()) {
      first = arrayField(sb, first, "antonyms", e.antonyms);
    }
    first = field(sb, first, "persian", e.persian);
    if (!e.persianAlternatives.isEmpty()) {
      first = arrayField(sb, first, "persianAlternatives",
          e.persianAlternatives);
    }
    first = field(sb, first, "english", e.english);
    first = field(sb, first, "phonetic", e.phonetic);
    first = field(sb, first, "memoryHook", e.memoryHook);
    first = field(sb, first, "scene", e.scene);
    first = field(sb, first, "cefr", e.cefr);
    first = field(sb, first, "source", e.source);
    first = field(sb, first, "dateSearched", e.dateSearched);
    first = field(sb, first, "note", e.note);
    sb.append("\n}\n");
    return sb.toString();
  }

  /** Builds the bulk file content: one JSON object holding one entry
      per saved word, in the order given. Each entry is built exactly
      like a per-word file; the bulk file adds no new fields and
      embeds no picture. */
  public static String buildBulkJson(List<Entry> entries) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"words\": [");
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) sb.append(",");
      sb.append("\n");
      String one = buildJson(entries.get(i)).trim();
      String[] lines = one.split("\n");
      for (int j = 0; j < lines.length; j++) {
        sb.append("    ").append(lines[j]);
        if (j < lines.length - 1) sb.append("\n");
      }
    }
    sb.append("\n  ]\n}\n");
    return sb.toString();
  }

  private static boolean field(StringBuilder sb, boolean first,
      String key, String value) {
    if (value == null || value.length() == 0) return first;
    if (!first) sb.append(",\n");
    sb.append("  ").append(JSONObject.quote(key)).append(": ")
        .append(JSONObject.quote(value));
    return false;
  }

  private static boolean arrayField(StringBuilder sb, boolean first,
      String key, List<String> values) {
    if (!first) sb.append(",\n");
    sb.append("  ").append(JSONObject.quote(key)).append(": [");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append(JSONObject.quote(values.get(i)));
    }
    sb.append("]");
    return false;
  }
}
