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
}
