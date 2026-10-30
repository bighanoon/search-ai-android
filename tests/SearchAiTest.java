package com.sobhan.searchai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import static org.junit.Assert.*;

/** JVM tests for the Android-free logic (GtxParser, WordIndex, ListCodec,
    DictParser) and for the generated word data. Run with JUnit 4. */
public class SearchAiTest {

  // ---- GtxParser ----

  @Test
  public void gtxSingleSegment() {
    GtxParser.Result r = GtxParser.parseGtx(
        "[[[\"\\u0633\\u0644\\u0627\\u0645\", \"hello\", null, null, 1]], null, \"en\"]",
        "hello");
    assertEquals(GtxParser.OK, r.status);
    assertEquals("\u0633\u0644\u0627\u0645", r.translation);
  }

  @Test
  public void gtxMultiSegment() {
    GtxParser.Result r = GtxParser.parseGtx(
        "[[[\"\\u0633\\u0644\\u0627\\u0645 \", \"hello \", null, null, 1],"
        + "[\"\\u062f\\u0646\\u06cc\\u0627\", \"world\", null, null, 1]], null, \"en\"]",
        "hello world");
    assertEquals(GtxParser.OK, r.status);
    assertEquals("\u0633\u0644\u0627\u0645 \u062f\u0646\u06cc\u0627", r.translation);
  }

  @Test
  public void gtxEscapedQuote() {
    GtxParser.Result r = GtxParser.parseGtx(
        "[[[\"a \\\"quoted\\\" word\", \"x\", null]], null, \"en\"]", "x");
    assertEquals(GtxParser.OK, r.status);
    assertEquals("a \"quoted\" word", r.translation);
  }

  @Test
  public void gtxNullSegmentSkipped() {
    GtxParser.Result r = GtxParser.parseGtx(
        "[[[null, \"hello\", null], [\"\\u0633\\u0644\\u0627\\u0645\", \"hello\", null]], null, \"en\"]",
        "hello");
    assertEquals(GtxParser.OK, r.status);
    assertEquals("\u0633\u0644\u0627\u0645", r.translation);
  }

  @Test
  public void gtxEmptyInputs() {
    assertEquals(GtxParser.EMPTY,
        GtxParser.parseGtx("[]", "hello").status);
    assertEquals(GtxParser.EMPTY,
        GtxParser.parseGtx("[[], null, \"en\"]", "hello").status);
    assertEquals(GtxParser.EMPTY,
        GtxParser.parseGtx(null, "hello").status);
  }

  @Test
  public void gtxHtmlAndTruncatedAreEmpty() {
    assertEquals(GtxParser.EMPTY,
        GtxParser.parseGtx("<html><body>error</body></html>", "hello").status);
    assertEquals(GtxParser.EMPTY,
        GtxParser.parseGtx("[[[\"\\u0633\\u0644\\u0627\\u0645\"", "hello").status);
  }

  @Test
  public void gtxEchoIsEmpty() {
    GtxParser.Result r = GtxParser.parseGtx(
        "[[[\"asdfgh\", \"asdfgh\", null]], null, \"en\"]", "asdfgh");
    assertEquals(GtxParser.EMPTY, r.status);
    assertNull(r.translation);
  }

  @Test
  public void myMemoryOkAndErrors() {
    assertEquals("\u0633\u0644\u0627\u0645", GtxParser.parseMyMemory(
        "{\"responseData\":{\"translatedText\":\"\\u0633\\u0644\\u0627\\u0645\","
        + "\"match\":1},\"responseStatus\":200}", "hello"));
    assertNull(GtxParser.parseMyMemory(
        "{\"responseData\":{\"translatedText\":\"\"},\"responseStatus\":200}", "hello"));
    assertNull(GtxParser.parseMyMemory(
        "{\"responseData\":{\"translatedText\":\"x\"},\"responseStatus\":429}", "hello"));
    assertNull(GtxParser.parseMyMemory(
        "{\"responseData\":{\"translatedText\":\"hello\"},\"responseStatus\":200}", "hello"));
  }

  // ---- WordIndex ----

  @Test
  public void findExactIsCaseInsensitive() {
    assertEquals("Austere", WordIndex.findExact("austere").word);
    assertEquals("Austere", WordIndex.findExact("AUSTERE").word);
    assertNull(WordIndex.findExact("nope"));
    assertNull(WordIndex.findExact("  "));
    assertNull(WordIndex.findExact(null));
  }

  @Test
  public void findSimilarSubstringAndEditDistance() {
    List<WordsData.Word> out = WordIndex.findSimilar("aust", 8);
    assertFalse(out.isEmpty());
    assertEquals("Austere", out.get(0).word);
    // "austera" is not a substring either way, but edit distance 1.
    boolean found = false;
    for (WordsData.Word w : WordIndex.findSimilar("austera", 8)) {
      if (w.word.equals("Austere")) found = true;
    }
    assertTrue(found);
    assertTrue(WordIndex.findSimilar("xyzqq", 8).isEmpty());
  }

  @Test
  public void isPersianDetection() {
    assertTrue(WordIndex.isPersian("\u0633\u0644\u0627\u0645"));
    assertTrue(WordIndex.isPersian("hello \u0633\u0644\u0627\u0645"));
    assertFalse(WordIndex.isPersian("hello"));
    assertFalse(WordIndex.isPersian(""));
    assertFalse(WordIndex.isPersian(null));
  }

  @Test
  public void normalizeFa() {
    // ZWNJ stripped, Arabic yeh/kaf converted, Persian digits mapped.
    assertEquals("\u0635\u0631\u0641\u0647\u062c\u0648",
        WordIndex.normalizeFa("\u0635\u0631\u0641\u0647\u200c\u062c\u0648"));
    assertEquals("\u06cc\u06a9",
        WordIndex.normalizeFa("\u064a\u0643"));
    assertEquals("123",
        WordIndex.normalizeFa("\u06f1\u06f2\u06f3"));
    assertEquals("",
        WordIndex.normalizeFa("   "));
  }

  @Test
  public void findByFaHits() {
    List<WordsData.Word> r1 = WordIndex.findByFa("\u062a\u0627\u0628\u200c\u0622\u0648\u0631", 8);
    assertEquals(1, r1.size());
    assertEquals("Resilient", r1.get(0).word);
    List<WordsData.Word> r2 = WordIndex.findByFa("\u0645\u0642\u062a\u0635\u062f", 8);
    assertEquals(1, r2.size());
    assertEquals("Frugal", r2.get(0).word);
    List<WordsData.Word> r3 = WordIndex.findByFa(
        "\u06cc\u062e \u0645\u062c\u0644\u0633 \u0631\u0627 \u0634\u06a9\u0633\u062a\u0646", 8);
    assertEquals(1, r3.size());
    assertEquals("Break the ice", r3.get(0).word);
    // Prefix of a gloss key still finds the word.
    assertFalse(WordIndex.findByFa("\u062a\u0627\u0628", 8).isEmpty());
    assertTrue(WordIndex.findByFa("xyz", 8).isEmpty());
    assertTrue(WordIndex.findByFa("", 8).isEmpty());
  }

  // ---- ListCodec ----

  @Test
  public void listCodecRoundTrip() {
    List<String> in = new ArrayList<String>();
    in.add("Austere");
    in.add("Frugal");
    List<String> out = ListCodec.decode(ListCodec.encode(in));
    assertEquals(in, out);
    assertTrue(ListCodec.decode("").isEmpty());
    assertTrue(ListCodec.decode(null).isEmpty());
    assertTrue(ListCodec.containsIgnoreCase(in, "austere"));
    assertFalse(ListCodec.containsIgnoreCase(in, "nope"));
  }

  // ---- Data integrity over WordsData ----

  @Test
  public void dataIntegrity() {
    assertEquals(35, WordsData.WORDS.length);
    Set<String> names = new HashSet<String>();
    for (WordsData.Word w : WordsData.WORDS) {
      assertFalse("duplicate: " + w.word, names.contains(w.word.toLowerCase()));
      names.add(w.word.toLowerCase());
      assertFalse(w.word.isEmpty());
      assertFalse(w.pos.isEmpty());
      assertFalse(w.phonetic.isEmpty());
      assertFalse(w.definition.isEmpty());
      assertFalse(w.definitionFa.isEmpty());
      assertFalse(w.example.isEmpty());
      assertFalse(w.exampleFa.isEmpty());
      assertFalse(w.synonyms.isEmpty());
      assertFalse(w.antonyms.isEmpty());
      assertTrue("definitionFa not Persian: " + w.word,
          WordIndex.isPersian(w.definitionFa));
      assertTrue("exampleFa not Persian: " + w.word,
          WordIndex.isPersian(w.exampleFa));
      checkNoAdjacentDuplicateTokens(w.definitionFa);
      checkNoAdjacentDuplicateTokens(w.exampleFa);
      checkCapitalizedList(w.synonyms);
      checkCapitalizedList(w.antonyms);
    }
    // Reverse index is sorted and in range.
    assertEquals(WordsData.FA_KEYS.length, WordsData.FA_WORD_IDX.length);
    assertTrue(WordsData.FA_KEYS.length > 0);
    for (int i = 1; i < WordsData.FA_KEYS.length; i++) {
      assertTrue(WordsData.FA_KEYS[i - 1].compareTo(WordsData.FA_KEYS[i]) <= 0);
    }
    for (int idx : WordsData.FA_WORD_IDX) {
      assertTrue(idx >= 0 && idx < WordsData.WORDS.length);
    }
  }

  private void checkNoAdjacentDuplicateTokens(String s) {
    String[] toks = s.split("\\s+");
    for (int i = 1; i < toks.length; i++) {
      assertFalse("adjacent duplicate token in: " + s,
          toks[i].equals(toks[i - 1]));
    }
  }

  private void checkCapitalizedList(String s) {
    String[] parts = s.split(", ");
    for (String p : parts) {
      assertTrue("not capitalized: " + p,
          p.length() > 0 && Character.isUpperCase(p.charAt(0)));
    }
  }

  // ---- DictParser (v3) ----

  // Fixture in dictionaryapi.dev shape; test data only, never app content.
  private static final String OUTLAW_JSON =
      "[{\"word\":\"outlaw\",\"phonetic\":\"ˈaʊtlɔː\",\"meanings\":["
      + "{\"partOfSpeech\":\"noun\",\"definitions\":["
      + "{\"definition\":\"A person who has broken the law and is wanted.\","
      + "\"example\":\"The outlaw hid in the hills.\","
      + "\"synonyms\":[\"fugitive\"],\"antonyms\":[]}],"
      + "\"synonyms\":[\"criminal\"],\"antonyms\":[\"citizen\"]}]}]";

  @Test
  public void dictParseFullEntry() {
    DictParser.DictEntry e = DictParser.parse(OUTLAW_JSON);
    assertNotNull(e);
    assertEquals("outlaw", e.word);
    assertEquals("ˈaʊtlɔː", e.phonetic);
    assertEquals("noun", e.pos);
    assertEquals(1, e.defs.size());
    assertEquals("A person who has broken the law and is wanted.",
        e.defs.get(0).definition);
    assertEquals("The outlaw hid in the hills.", e.defs.get(0).example);
    assertTrue(e.synonyms.contains("criminal"));
    assertTrue(e.synonyms.contains("fugitive"));
    assertTrue(e.antonyms.contains("citizen"));
  }

  @Test
  public void dictParseRejectsEmpty() {
    assertNull(DictParser.parse(null));
    assertNull(DictParser.parse("[]"));
    assertNull(DictParser.parse("not json"));
    assertNull(DictParser.parse("[{\"word\":\"x\",\"meanings\":[]}]"));
    assertNull(DictParser.parse(
        "[{\"word\":\"x\",\"meanings\":[{\"partOfSpeech\":\"noun\","
        + "\"definitions\":[{\"definition\":\"\"}]}]}]"));
  }

  @Test
  public void dictJsonRoundTrip() {
    DictParser.DictEntry e = DictParser.parse(OUTLAW_JSON);
    assertNotNull(e);
    DictParser.DictEntry back = DictParser.fromJson(DictParser.toJson(e));
    assertNotNull(back);
    assertEquals(e.word, back.word);
    assertEquals(e.phonetic, back.phonetic);
    assertEquals(e.pos, back.pos);
    assertEquals(e.defs.size(), back.defs.size());
    assertEquals(e.defs.get(0).definition, back.defs.get(0).definition);
    assertEquals(e.defs.get(0).example, back.defs.get(0).example);
    assertEquals(e.synonyms, back.synonyms);
    assertEquals(e.antonyms, back.antonyms);
    assertNull(DictParser.fromJson("garbage"));
    assertNull(DictParser.fromJson(null));
  }

  @Test
  public void dictPhoneticFallback() {
    DictParser.DictEntry e = DictParser.parse(
        "[{\"word\":\"test\",\"phonetics\":[{\"text\":\"tɛst\"}],"
        + "\"meanings\":[{\"partOfSpeech\":\"noun\",\"definitions\":"
        + "[{\"definition\":\"A trial.\"}]}]}]");
    assertNotNull(e);
    assertEquals("tɛst", e.phonetic);
  }

  // ---- cefrFor (v3): only verified levels, nothing invented ----

  @Test
  public void cefrOnlyVerified() {
    assertEquals("C1", WordIndex.cefrFor("Favoritism"));
    assertEquals("C1", WordIndex.cefrFor("favoritism"));
    assertEquals("B2", WordIndex.cefrFor("Interpretation"));
    assertEquals("A2", WordIndex.cefrFor("Helpful"));
    assertEquals("C2", WordIndex.cefrFor("Transcendence"));
    assertEquals("C1", WordIndex.cefrFor("Eloquent"));
    assertNull(WordIndex.cefrFor("Austere"));
    assertNull(WordIndex.cefrFor("Outlaw"));
    assertNull(WordIndex.cefrFor(null));
  }

  // ---- v4: multi-entry dictionary parsing (the rebel bug) ----
  // The live API returns TWO entries for "rebel" (noun + verb). v3
  // read only root[0] and dropped the verb entry, including its
  // synonyms ("defy") and antonyms ("obey", "submit"). v4 merges
  // every entry. This fixture is the real API shape, trimmed.

  private static final String REBEL_JSON =
      "[{\"word\":\"rebel\",\"phonetic\":\"/ˈɹɛbəl/\",\"meanings\":["
      + "{\"partOfSpeech\":\"noun\",\"definitions\":["
      + "{\"definition\":\"A person who resists an established authority, often violently\"},"
      + "{\"definition\":\"A person from the Confederate States of America\"}],"
      + "\"synonyms\":[],\"antonyms\":[]}]},"
      + "{\"word\":\"rebel\",\"phonetic\":\"/ɹɪˈbɛl/\",\"meanings\":["
      + "{\"partOfSpeech\":\"verb\",\"definitions\":["
      + "{\"definition\":\"To resist or become defiant toward an authority.\"}],"
      + "\"synonyms\":[\"defy\"],\"antonyms\":[\"obey\",\"submit\"]}]}]";

  @Test
  public void dictParseMultiEntry() {
    DictParser.DictEntry e = DictParser.parse(REBEL_JSON);
    assertNotNull(e);
    assertEquals("rebel", e.word);
    assertEquals(3, e.defs.size());
    assertEquals("noun", e.defs.get(0).pos);
    assertEquals("A person who resists an established authority, often violently",
        e.defs.get(0).definition);
    assertEquals("verb", e.defs.get(2).pos);
    assertEquals("To resist or become defiant toward an authority.",
        e.defs.get(2).definition);
    assertTrue(e.synonyms.contains("defy"));
    assertTrue(e.antonyms.contains("obey"));
    assertTrue(e.antonyms.contains("submit"));
    // Neither entry carries an example: the honest card shows a note,
    // never an invented example. The parser stores a missing example
    // as an empty string, which the renderer treats as "none".
    assertEquals("", e.defs.get(0).example);
    assertEquals("", e.defs.get(2).example);
  }

  @Test
  public void dictPosRoundTrip() {
    DictParser.DictEntry e = DictParser.parse(OUTLAW_JSON);
    assertNotNull(e);
    String json = DictParser.toJson(e);
    assertNotNull(json);
    DictParser.DictEntry back = DictParser.fromJson(json);
    assertNotNull(back);
    assertEquals(e.defs.size(), back.defs.size());
    for (int i = 0; i < e.defs.size(); i++) {
      assertEquals(e.defs.get(i).pos, back.defs.get(i).pos);
      assertEquals(e.defs.get(i).definition, back.defs.get(i).definition);
    }
  }

  // ---- v4: verified yelling entry (transcribed from the web
  // result screenshot, never invented) ----

  @Test
  public void verifiedYelling() {
    VerifiedEntries.Entry v = VerifiedEntries.get("yelling");
    assertNotNull(v);
    assertEquals("yelling", v.word);
    assertEquals("The act of making a loud vocal sound, often in anger or excitement.",
        v.definition);
    assertEquals("He yelled directions to the party from the car.", v.example);
    assertEquals(5, v.alternatives.size());
    assertEquals("B1", v.cefr);
    assertNotNull(v.hook);
    assertTrue(v.hook.indexOf("wide-open mouth") >= 0);
    assertNotNull(v.scene);
    assertEquals(2, v.meanings.size());
  }

  @Test
  public void hookFor() {
    assertNotNull(VerifiedEntries.hookFor("austere"));
    assertNotNull(VerifiedEntries.hookFor("yelling"));
    assertNull(VerifiedEntries.hookFor("outlaw"));
    assertNull(VerifiedEntries.hookFor(null));
  }

  @Test
  public void cefrYelling() {
    assertEquals("B1", WordIndex.cefrFor("yelling"));
    assertEquals("B1", WordIndex.cefrFor("YELLING"));
  }

  // ---- v5: full dictionary card (nothing dropped) ----

  private static final String AMPLE_JSON =
      "[{\"word\":\"ample\",\"meanings\":[{\"partOfSpeech\":\"adj\","
      + "\"definitions\":[{\"definition\":\"Enough.\",\"example\":\"Ample time.\","
      + "\"synonyms\":[\"plenty\"],\"antonyms\":[\"scarce\"]},"
      + "{\"definition\":\"Large.\",\"example\":\"Ample room.\","
      + "\"synonyms\":[\"spacious\"],\"antonyms\":[\"tiny\"]}],"
      + "\"synonyms\":[\"sufficient\"],\"antonyms\":[\"meager\"]}]},"
      + "{\"word\":\"ample\",\"meanings\":[{\"partOfSpeech\":\"noun\","
      + "\"definitions\":[{\"definition\":\"An example.\","
      + "\"example\":\"An ample of courage.\",\"synonyms\":[\"sample\"],"
      + "\"antonyms\":[]}]}]}]";

  @Test
  public void dictParseCompleteness() {
    DictParser.DictEntry e = DictParser.parse(AMPLE_JSON);
    assertNotNull(e);
    assertEquals(3, e.defs.size());
    assertEquals("adj", e.defs.get(0).pos);
    assertEquals("Enough.", e.defs.get(0).definition);
    assertEquals("Ample time.", e.defs.get(0).example);
    assertEquals("noun", e.defs.get(2).pos);
    assertEquals("An ample of courage.", e.defs.get(2).example);
    assertTrue(e.synonyms.contains("plenty"));
    assertTrue(e.synonyms.contains("spacious"));
    assertTrue(e.synonyms.contains("sufficient"));
    assertTrue(e.synonyms.contains("sample"));
    assertTrue(e.antonyms.contains("scarce"));
    assertTrue(e.antonyms.contains("tiny"));
    assertTrue(e.antonyms.contains("meager"));
  }

  @Test
  public void dictCapsRaised() {
    StringBuilder sb = new StringBuilder();
    sb.append("[{\"word\":\"many\",\"meanings\":[{\"partOfSpeech\":\"adj\","
        + "\"definitions\":[");
    for (int i = 0; i < 10; i++) {
      if (i > 0) sb.append(",");
      sb.append("{\"definition\":\"Sense ").append(i).append(".\"}");
    }
    sb.append("]}]}]");
    DictParser.DictEntry e = DictParser.parse(sb.toString());
    assertNotNull(e);
    assertEquals(10, e.defs.size());
    assertEquals("Sense 9.", e.defs.get(9).definition);
  }

  @Test
  public void dictJsonRoundTripCompleteness() {
    DictParser.DictEntry e = DictParser.parse(AMPLE_JSON);
    assertNotNull(e);
    DictParser.DictEntry back =
        DictParser.fromJson(DictParser.toJson(e));
    assertNotNull(back);
    assertEquals(e.defs.size(), back.defs.size());
    for (int i = 0; i < e.defs.size(); i++) {
      assertEquals(e.defs.get(i).pos, back.defs.get(i).pos);
      assertEquals(e.defs.get(i).definition, back.defs.get(i).definition);
      assertEquals(e.defs.get(i).example, back.defs.get(i).example);
    }
    assertEquals(e.synonyms, back.synonyms);
    assertEquals(e.antonyms, back.antonyms);
  }

  // ---- v5: ChatGPT sections parser (strict top level, lenient fields) ----

  @Test
  public void chatGptParseValid() {
    ChatGptSections.Sections s = ChatGptSections.parse(
        "{\"overview\":\"O.\",\"background\":\"B.\","
        + "\"hook\":\"H.\",\"answer\":\"A.\"}");
    assertNotNull(s);
    assertEquals("O.", s.overview);
    assertEquals("B.", s.background);
    assertEquals("H.", s.hook);
    assertEquals("A.", s.answer);
    assertTrue(ChatGptSections.hasAny(s));
  }

  @Test
  public void chatGptParsePartial() {
    ChatGptSections.Sections s = ChatGptSections.parse(
        "{\"overview\":\"O.\",\"answer\":\"A.\"}");
    assertNotNull(s);
    assertEquals("O.", s.overview);
    assertEquals("A.", s.answer);
    assertNull(s.background);
    assertNull(s.hook);
  }

  @Test
  public void chatGptParseInvalid() {
    assertNull(ChatGptSections.parse(null));
    assertNull(ChatGptSections.parse("not json"));
    assertNull(ChatGptSections.parse("[1, 2]"));
    assertNull(ChatGptSections.parse("\"just a string\""));
    ChatGptSections.Sections empty = ChatGptSections.parse("{}");
    assertNotNull(empty);
    assertFalse(ChatGptSections.hasAny(empty));
    // A wrongly typed field is omitted, not fatal to the rest.
    ChatGptSections.Sections mixed = ChatGptSections.parse(
        "{\"overview\":42,\"answer\":\"A.\"}");
    assertNotNull(mixed);
    assertNull(mixed.overview);
    assertEquals("A.", mixed.answer);
  }

  // ---- v5: popup alternatives (MyMemory matches) ----

  @Test
  public void myMemoryMatches() {
    String json = "{\"responseData\":{\"translatedText\":\"x\",\"match\":1},"
        + "\"responseStatus\":200,\"matches\":["
        + "{\"translation\":\"main\",\"quality\":\"100\"},"
        + "{\"translation\":\"first\",\"quality\":\"80\"},"
        + "{\"translation\":\"learned\",\"quality\":\"70\"},"
        + "{\"translation\":\"first\",\"quality\":\"60\"},"
        + "{\"translation\":\"\",\"quality\":\"50\"},"
        + "{\"translation\":\"second\",\"quality\":\"40\"},"
        + "{\"translation\":\"third\",\"quality\":\"30\"},"
        + "{\"translation\":\"fourth\",\"quality\":\"20\"},"
        + "{\"translation\":\"fifth\",\"quality\":\"10\"}]}";
    List<String> alts =
        GtxParser.parseMyMemoryMatches(json, "main", "learned");
    assertEquals(5, alts.size());
    assertEquals("first", alts.get(0));
    assertEquals("second", alts.get(1));
    assertEquals("third", alts.get(2));
    assertEquals("fourth", alts.get(3));
    assertEquals("fifth", alts.get(4));
    assertTrue(GtxParser.parseMyMemoryMatches("garbage", "m", "q").isEmpty());
    assertTrue(GtxParser.parseMyMemoryMatches(
        "{\"responseStatus\":403,\"matches\":[]}", "m", "q").isEmpty());
  }

  @Test
  public void isPersianMixedPhrase() {
    assertTrue(WordIndex.isPersian("hello سخت"));
    assertFalse(WordIndex.isPersian("hello"));
  }

  // ---- v5: word-file export (data only, never a picture) ----

  private WordFileBuilder.Entry fullEntry() {
    WordFileBuilder.Entry e = new WordFileBuilder.Entry();
    e.word = "Austere";
    e.partsOfSpeech.add("adj.");
    e.definitions.add(new WordFileBuilder.Def("adj.",
        "Severe or strict.", "The austere room was bare."));
    e.synonyms.add("Stern");
    e.antonyms.add("Lavish");
    e.persian = "سخت‌گیر";
    e.persianAlternatives.add("جدی");
    e.phonetic = "aw-STEER";
    e.memoryHook = "Picture a steer in a bare barn.";
    e.scene = "A bare room, almost nothing in it.";
    e.cefr = "B1";
    e.source = "bundled";
    e.dateSearched = "2026-10-29";
    return e;
  }

  @Test
  public void wordFileBundled() throws Exception {
    String json = WordFileBuilder.buildJson(fullEntry());
    org.json.JSONObject o = new org.json.JSONObject(json);
    assertEquals("Austere", o.getString("word"));
    assertEquals("adj.", o.getJSONArray("partsOfSpeech").getString(0));
    assertEquals(1, o.getJSONArray("definitions").length());
    assertEquals("Severe or strict.",
        o.getJSONArray("definitions").getJSONObject(0)
            .getString("definition"));
    assertEquals("The austere room was bare.",
        o.getJSONArray("definitions").getJSONObject(0)
            .getString("example"));
    assertEquals("Stern", o.getJSONArray("synonyms").getString(0));
    assertEquals("Lavish", o.getJSONArray("antonyms").getString(0));
    assertEquals("سخت‌گیر", o.getString("persian"));
    assertEquals("جدی", o.getJSONArray("persianAlternatives").getString(0));
    assertEquals("aw-STEER", o.getString("phonetic"));
    assertEquals("Picture a steer in a bare barn.", o.getString("memoryHook"));
    assertEquals("A bare room, almost nothing in it.", o.getString("scene"));
    assertEquals("B1", o.getString("cefr"));
    assertEquals("bundled", o.getString("source"));
    assertEquals("2026-10-29", o.getString("dateSearched"));
    assertFalse(json.contains("\"image\""));
    assertFalse(json.contains("\"picture\""));
    assertFalse(json.contains("base64"));
  }

  @Test
  public void wordFilePhraseFilename() {
    assertEquals("heads-up.json", WordFileBuilder.filenameFor("heads up"));
    assertEquals("break-the-ice.json",
        WordFileBuilder.filenameFor("Break the ice"));
    assertEquals("austere.json", WordFileBuilder.filenameFor("austere"));
  }

  @Test
  public void wordFileTranslationOnlyOmissions() throws Exception {
    WordFileBuilder.Entry e = new WordFileBuilder.Entry();
    e.word = "outlaw";
    e.persian = "قانون‌شکن";
    e.source = "translation";
    e.dateSearched = "2026-10-29";
    e.note = "Full entry not available.";
    String json = WordFileBuilder.buildJson(e);
    org.json.JSONObject o = new org.json.JSONObject(json);
    assertEquals("outlaw", o.getString("word"));
    assertEquals("قانون‌شکن", o.getString("persian"));
    assertEquals("Full entry not available.", o.getString("note"));
    assertFalse(o.has("definitions"));
    assertFalse(o.has("synonyms"));
    assertFalse(o.has("antonyms"));
    assertFalse(o.has("memoryHook"));
    assertFalse(o.has("scene"));
    assertFalse(o.has("cefr"));
    assertFalse(o.has("phonetic"));
  }

  // ---- v6: stale-cache invalidation, pictures, bulk file ----

  @Test
  public void dictCacheVersionRejectsOld() {
    // A pre-v6 cache (no version stamp, noun-only shape like the v3
    // rebel cache) must be rejected so the lookup re-fetches.
    String oldJson = "{\"word\":\"rebel\",\"phonetic\":\"\",\"pos\":\"noun\","
        + "\"defs\":[{\"d\":\"A person who resists an established authority, often violently\",\"e\":\"\",\"p\":\"noun\"}],"
        + "\"syn\":[],\"ant\":[]}";
    assertNull(DictParser.fromJson(oldJson));
    // A v6 cache round-trips the full merged entry.
    DictParser.DictEntry e = DictParser.parse(REBEL_JSON);
    assertNotNull(e);
    DictParser.DictEntry back = DictParser.fromJson(DictParser.toJson(e));
    assertNotNull(back);
    assertEquals(3, back.defs.size());
    assertEquals("verb", back.defs.get(2).pos);
    assertTrue(back.synonyms.contains("defy"));
    assertTrue(back.antonyms.contains("obey"));
    assertTrue(back.antonyms.contains("submit"));
  }

  @Test
  public void pictureNames() {
    assertEquals("a_piece_of_cake", PictureNames.stem("A piece of cake"));
    assertEquals("pic_a_piece_of_cake",
        PictureNames.drawableName("A piece of cake"));
    assertEquals("a_piece_of_cake.webp",
        PictureNames.fileName("A piece of cake"));
    assertEquals("yelling", PictureNames.stem("yelling"));
    assertEquals("pic_yelling", PictureNames.drawableName("yelling"));
    assertEquals("rebel", PictureNames.stem("Rebel"));
    assertEquals("", PictureNames.stem("   "));
    assertEquals("", PictureNames.drawableName(null));
  }

  @Test
  public void sceneTexts() {
    assertNotNull(SceneTexts.forWord("austere"));
    assertNotNull(SceneTexts.forWord("Austere"));
    // Tightened under the closest-scene rule: candid is no longer
    // the singer interview, bolster is no longer a growing argument.
    assertTrue(SceneTexts.forWord("candid").indexOf("face to face") >= 0);
    assertTrue(SceneTexts.forWord("bolster").indexOf("lawyer") >= 0);
    assertNotNull(SceneTexts.forWord("rebel"));
    assertNotNull(SceneTexts.forWord("outlaw"));
    // Yelling's scene lives in VerifiedEntries, not here.
    assertNull(SceneTexts.forWord("yelling"));
    assertNull(SceneTexts.forWord("nonexistentword"));
    assertNull(SceneTexts.forWord(null));
  }

  @Test
  public void updatesManifestParse() throws Exception {
    String json = "{\"pictures\":[{\"word\":\"yelling\",\"file\":\"yelling.webp\",\"version\":1},"
        + "{\"word\":\"rebel\",\"file\":\"rebel.webp\",\"version\":2},"
        + "{\"word\":\"bad\",\"file\":\"bad.webp\"},"
        + "{\"word\":\"evil\",\"file\":\"../evil.webp\",\"version\":1}]}";
    List<UpdatesManifest.Picture> pics = UpdatesManifest.parse(json);
    assertEquals(2, pics.size());
    assertEquals("yelling", pics.get(0).word);
    assertEquals("yelling.webp", pics.get(0).file);
    assertEquals(1, pics.get(0).version);
    assertEquals(2, pics.get(1).version);
    assertTrue(UpdatesManifest.parse("not json").isEmpty());
    assertTrue(UpdatesManifest.parse(null).isEmpty());
  }

  @Test
  public void bulkFileJson() throws Exception {
    WordFileBuilder.Entry a = new WordFileBuilder.Entry();
    a.word = "rebel";
    a.definitions.add(new WordFileBuilder.Def("verb",
        "To resist or become defiant toward an authority.", ""));
    a.synonyms.add("defy");
    a.antonyms.add("obey");
    WordFileBuilder.Entry b = new WordFileBuilder.Entry();
    b.word = "outlaw";
    b.persian = "test-persian";
    b.note = "Full entry not available.";
    List<WordFileBuilder.Entry> entries = new ArrayList<WordFileBuilder.Entry>();
    entries.add(a);
    entries.add(b);
    String json = WordFileBuilder.buildBulkJson(entries);
    org.json.JSONObject o = new org.json.JSONObject(json);
    org.json.JSONArray words = o.getJSONArray("words");
    assertEquals(2, words.length());
    assertEquals("rebel", words.getJSONObject(0).getString("word"));
    assertEquals("defy",
        words.getJSONObject(0).getJSONArray("synonyms").getString(0));
    assertEquals("outlaw", words.getJSONObject(1).getString("word"));
    assertFalse(words.getJSONObject(1).has("definitions"));
    assertFalse(json.contains("picture"));
  }

  // ---- v7: DetailContent ----

  @Test
  public void boldRangesCaseInsensitive() {
    List<DetailContent.Range> rs =
        DetailContent.boldRanges("A lone wolf howled at the night.", "night");
    assertEquals(1, rs.size());
    assertEquals(26, rs.get(0).start);
    assertEquals(31, rs.get(0).end);
    // Multi-word phrase matches as one phrase.
    List<DetailContent.Range> ph =
        DetailContent.boldRanges("Call it a day, call it a day.", "call it a day");
    assertEquals(2, ph.size());
    // No match, blank word, and null input all mean "no bolding".
    assertTrue(DetailContent.boldRanges("hello", "night").isEmpty());
    assertTrue(DetailContent.boldRanges("hello", "  ").isEmpty());
    assertTrue(DetailContent.boldRanges(null, "night").isEmpty());
  }

  @Test
  public void collectExamplesDedupes() {
    List<String> raw = new ArrayList<String>();
    raw.add("He yelled.");
    raw.add("He yelled.");
    raw.add("");
    raw.add(null);
    raw.add("She stayed up late.");
    List<String> out = DetailContent.collectExamples(raw);
    assertEquals(2, out.size());
    assertEquals("He yelled.", out.get(0));
    assertEquals("She stayed up late.", out.get(1));
  }

  @Test
  public void relatedWordsDedupesAndCaps() {
    List<String> syn = new ArrayList<String>();
    syn.add("darkness");
    syn.add("evening");
    List<String> sim = new ArrayList<String>();
    sim.add("Evening");
    sim.add("midnight");
    sim.add("dusk");
    List<String> out = DetailContent.relatedWords(syn, sim);
    assertEquals(4, out.size());
    assertEquals("darkness", out.get(0));
    assertEquals("evening", out.get(1));
    assertEquals("midnight", out.get(2));
    assertEquals("dusk", out.get(3));
  }

  @Test
  public void showMoreSplit() {
    List<String> all = new ArrayList<String>();
    all.add("a");
    all.add("b");
    all.add("c");
    all.add("d");
    all.add("e");
    assertEquals(3, DetailContent.firstExamples(all).size());
    assertEquals(2, DetailContent.restExamples(all).size());
    assertEquals("d", DetailContent.restExamples(all).get(0));
    List<String> two = new ArrayList<String>();
    two.add("a");
    two.add("b");
    assertEquals(2, DetailContent.firstExamples(two).size());
    assertTrue(DetailContent.restExamples(two).isEmpty());
    assertTrue(DetailContent.firstExamples(null).isEmpty());
  }

  @Test
  public void frequencyScale() {
    assertEquals(5, DetailContent.frequencyLevelForLabel("Common"));
    assertEquals(1, DetailContent.frequencyLevelForLabel("very rare"));
    assertEquals(7, DetailContent.frequencyLevelForLabel("Extremely common"));
    assertEquals(-1, DetailContent.frequencyLevelForLabel("sometimes"));
    assertEquals(-1, DetailContent.frequencyLevelForLabel(null));
    assertEquals("Common", DetailContent.frequencyLabelForLevel(5));
    assertEquals(null, DetailContent.frequencyLabelForLevel(0));
    assertEquals(null, DetailContent.frequencyLabelForLevel(8));
    assertTrue(DetailContent.validFrequencyLevel(1));
    assertTrue(DetailContent.validFrequencyLevel(7));
    assertFalse(DetailContent.validFrequencyLevel(0));
    assertFalse(DetailContent.validFrequencyLevel(8));
  }

  // ---- v7: ChatGptSections frequency fields ----

  @Test
  public void chatGptFrequencyParses() throws Exception {
    String json = "{\"overview\":\"o\",\"frequencyLabel\":\"Common\","
        + "\"frequencyLevel\":5}";
    ChatGptSections.Sections s = ChatGptSections.parse(json);
    assertEquals("Common", s.frequencyLabel);
    assertEquals(5, s.frequencyLevel);
    // Out-of-range level parses to -1 (omit the meter), label kept.
    String bad = "{\"frequencyLabel\":\"Common\",\"frequencyLevel\":9}";
    ChatGptSections.Sections s2 = ChatGptSections.parse(bad);
    assertEquals(-1, s2.frequencyLevel);
    assertEquals("Common", s2.frequencyLabel);
    // Missing frequency fields stay absent, never invented.
    ChatGptSections.Sections s3 =
        ChatGptSections.parse("{\"overview\":\"o\"}");
    assertEquals(null, s3.frequencyLabel);
    assertEquals(-1, s3.frequencyLevel);
    // Unparseable JSON still returns null (dictionary fallback).
    assertEquals(null, ChatGptSections.parse("not json"));
  }

  // ---- v8: My Words / Try a word sections on the result page ----

  @Test
  public void v8HomePageShowsSections() {
    // No result shown (empty result box): home page shows both sections.
    assertFalse(ResultPageSections.resultShown(0));
    assertTrue(ResultPageSections.sectionsVisible(false));
  }

  @Test
  public void v8ResultPageHidesSections() {
    // Any result content (one child or many): result page hides both.
    assertTrue(ResultPageSections.resultShown(1));
    assertTrue(ResultPageSections.resultShown(5));
    assertFalse(ResultPageSections.sectionsVisible(true));
  }

  @Test
  public void v8RuleDependsOnlyOnResultPresence() {
    // The rule is a pure function of whether a result is shown: it
    // takes no word, saved-set, or preference input, so it cannot
    // change saved-word data whichever words are saved.
    for (int count = 0; count <= 10; count++) {
      assertEquals(count == 0,
          ResultPageSections.sectionsVisible(
              ResultPageSections.resultShown(count)));
    }
  }

  // ---- v9: duplicate result header word removed, X kept ----

  @Test
  public void v9ResultHeaderHasNoWordHeading() {
    // The large word heading above the tabs is gone: it duplicated
    // the search header and the word card.
    assertFalse(ResultHeader.hasWordHeading());
  }

  @Test
  public void v10ResultHeaderHasNoCloseControl() {
    // v10 supersedes v9 here: the small X close control is removed
    // entirely; the result block starts directly with the tabs row,
    // and system Back clears the result instead.
    assertFalse(ResultHeader.hasCloseControl());
    assertTrue(ResultHeader.backClearsResult());
  }

  @Test
  public void v9CloseClearsResultAndRestoresHomeSections() {
    // Tapping X empties the result box, and an empty result box
    // means the home page: My Words and Try a word are visible again.
    int afterClose = ResultHeader.childCountAfterClose();
    assertEquals(0, afterClose);
    assertFalse(ResultPageSections.resultShown(afterClose));
    assertTrue(ResultPageSections.sectionsVisible(
        ResultPageSections.resultShown(afterClose)));
  }

  @Test
  public void v9HeaderRuleTouchesNoSavedData() {
    // The header model takes no word or saved-set input at all, so
    // removing the heading cannot change, clear, or delete saved words.
    assertFalse(ResultHeader.hasWordHeading());
    assertFalse(ResultHeader.hasCloseControl());
    assertTrue(ResultHeader.backClearsResult());
    assertEquals(0, ResultHeader.childCountAfterClose());
  }

  // ---- v10: result-page extras hidden, X removed, Back clears ----

  @Test
  public void v10ResultPageHidesExtras() {
    // When a result is shown, the tip line, History/Settings pills,
    // and Updating cards pill are hidden, alongside My Words and
    // Try a word.
    assertTrue(ResultPageSections.resultShown(1));
    assertFalse(ResultPageSections.extrasVisible(true));
    assertFalse(ResultPageSections.sectionsVisible(true));
  }

  @Test
  public void v10HomePageShowsExtrasAgain() {
    // When no result is shown (home page), the tip line,
    // History/Settings pills, and Updating cards pill are present
    // again, exactly as in v9, alongside My Words and Try a word.
    assertFalse(ResultPageSections.resultShown(0));
    assertTrue(ResultPageSections.extrasVisible(false));
    assertTrue(ResultPageSections.sectionsVisible(false));
  }

  @Test
  public void v10BackClearsResultAndRestoresHome() {
    // Back on a result empties the result box, and an empty result
    // box means the home page: My Words, Try a word, and the extras
    // (tip line, History/Settings pills, Updating cards pill) are
    // all visible again.
    int afterBack = ResultHeader.childCountAfterClose();
    assertEquals(0, afterBack);
    assertFalse(ResultPageSections.resultShown(afterBack));
    assertTrue(ResultPageSections.sectionsVisible(
        ResultPageSections.resultShown(afterBack)));
    assertTrue(ResultPageSections.extrasVisible(
        ResultPageSections.resultShown(afterBack)));
  }

  @Test
  public void v10ExtrasRuleDependsOnlyOnResultPresence() {
    // The extras rule is a pure function of whether a result is
    // shown: it takes no word, saved-set, or preference input, so
    // hiding the extras on a result page cannot change saved-word
    // data or downloaded pictures whichever words are saved.
    for (int count = 0; count <= 10; count++) {
      assertEquals(count == 0,
          ResultPageSections.extrasVisible(
              ResultPageSections.resultShown(count)));
    }
  }

  @Test
  public void v10ShowCardIsCenteredWithPictureOnTop() {
    // The Show card popup sits in the middle of the screen (not a
    // full-height or bottom sheet), with the picture panel on top
    // when a picture exists and no panel at all when there is none
    // (never a fake picture).
    assertTrue(ShowCardLayout.popupCentered());
    assertFalse(ShowCardLayout.popupIsFullHeightSheet());
    assertTrue(ShowCardLayout.pictureOnTop());
    assertTrue(ShowCardLayout.noPicturePanelWhenNoPicture());
  }

  @Test
  public void v10TellMeMoreIsCompact() {
    // Tell Me More is compact, about the size of its text, not the
    // old wide full-width button.
    assertTrue(ShowCardLayout.tellMeMoreCompact());
    assertFalse(ShowCardLayout.tellMeMoreFullWidth());
  }

  @Test
  public void v10CardRulesTouchNoSavedData() {
    // The card layout model takes no word or saved-set input at
    // all, so centering the popup, putting the picture on top, and
    // shrinking Tell Me More cannot change saved words or delete
    // downloaded pictures.
    assertTrue(ShowCardLayout.popupCentered());
    assertTrue(ShowCardLayout.pictureOnTop());
    assertTrue(ShowCardLayout.tellMeMoreCompact());
  }
}
