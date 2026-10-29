package com.sobhan.searchai;

import java.util.ArrayList;
import java.util.List;

/** Verified entries and memory hooks, transcribed from user-provided
    screenshots only. Android-free and unit-testable on the JVM.

    Nothing here is invented: every string below is copied from a
    screenshot the user sent. A word with no verified entry or hook
    returns null, and the UI shows no hook and no made-up content. */
public class VerifiedEntries {

  public static class Entry {
    public String word;
    public String pos;
    public String definition;
    public String example;
    public String fa;
    public List<String> alternatives;
    public String cefr;
    public String hook;
    public String hookAttribution;
    public String scene;
    public List<DictParser.Def> meanings;
    public Entry(String word, String pos, String definition, String example,
        String fa, List<String> alternatives, String cefr, String hook,
        String hookAttribution, String scene,
        List<DictParser.Def> meanings) {
      this.word = word;
      this.pos = pos;
      this.definition = definition;
      this.example = example;
      this.fa = fa;
      this.alternatives = alternatives;
      this.cefr = cefr;
      this.hook = hook;
      this.hookAttribution = hookAttribution;
      this.scene = scene;
      this.meanings = meanings;
    }
  }

  /** Memory hook for a word, or null when none is verified. */
  public static String hookFor(String word) {
    if (word == null) return null;
    String w = word.trim().toLowerCase();
    if (w.equals("austere")) {
      return "Picture a steer (AUSTERE sounds like 'a steer') standing in "
          + "an empty stone barn in winter \u2014 no hay, no blankets, just "
          + "cold, bare, and serious.";
    }
    if (w.equals("yelling")) {
      return "Picture a parent with a wide-open mouth, arms raised, calling "
          + "loudly for their child to come home \u2014 that's yelling: "
          + "making a loud vocal sound.";
    }
    return null;
  }

  /** Attribution line for a hook, or null when none was transcribed. */
  public static String hookAttributionFor(String word) {
    if (word == null) return null;
    if (word.trim().equalsIgnoreCase("yelling")) {
      return "Written by ChatGPT to help you remember it.";
    }
    return null;
  }

  /** The verified entry for a word, or null when none is verified. */
  public static Entry get(String word) {
    if (word == null) return null;
    if (!word.trim().equalsIgnoreCase("yelling")) return null;
    List<String> alternatives = new ArrayList<String>();
    alternatives.add("\u062f\u0627\u062f \u0632\u062f\u0646");
    alternatives.add("\u0641\u0631\u06cc\u0627\u062f \u06a9\u0634\u06cc\u062f\u0646");
    alternatives.add("\u062c\u06cc\u063a \u0632\u062f\u0646");
    alternatives.add("\u0647\u0648\u0627\u0631 \u06a9\u0634\u06cc\u062f\u0646");
    alternatives.add("\u0628\u0627\u0646\u06af \u0632\u062f\u0646");
    List<DictParser.Def> meanings = new ArrayList<DictParser.Def>();
    meanings.add(new DictParser.Def(
        "The act of making a loud vocal sound, often in anger or excitement.",
        "He yelled directions to the party from the car.",
        "noun."));
    meanings.add(new DictParser.Def(
        "Shout; holler; make a loud sound with the voice.",
        "He yelled directions to the party from the car.",
        "n."));
    return new Entry("yelling", "noun.",
        "The act of making a loud vocal sound, often in anger or excitement.",
        "He yelled directions to the party from the car.",
        "\u0641\u0631\u06cc\u0627\u062f \u0632\u062f\u0646",
        alternatives, "B1", hookFor("yelling"),
        hookAttributionFor("yelling"),
        "Marcus leans from his car, shouting directions loudly \u2014 that "
        + "is yelling: making a loud sound with your voice.",
        meanings);
  }
}
