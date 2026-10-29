package com.sobhan.searchai;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

/** Search AI v5: v4's exact web match, plus a working
    select-to-translate popup, the full dictionary card, a
    Settings-only ChatGPT API option, and word-file export.

    Theme, buttons, icons, chips, and information all follow the
    user's web screenshots (home, result page, Show card). The web
    picture panel is the one unmatched element: this key-free app
    cannot generate pictures, so the Show card shows its caption and
    scene text as a text panel, never a fake picture, and has no
    Repaint button (it would be dead without a picture). */
public class MainActivity extends Activity {
  // Web theme, sampled from the user's screenshots where measurable
  // (page background, card background, Look up pill) and matched by
  // eye elsewhere. No purple accent anywhere in v4.
  private static final int BG = 0xFF0B0A0F;
  private static final int CARD = 0xFF1C1C24;
  private static final int CARD2 = 0xFF26262F;
  private static final int TEXT = 0xFFFFFFFF;
  private static final int SUB = 0xFFA3A3AD;
  private static final int HEADING = 0xFF8A8580;
  private static final int ACCENT = 0xFF65271D;
  private static final int GREEN = 0xFF34D399;
  private static final int TEAL = 0xFF2DD4BF;
  private static final int BLUE = 0xFF3B82F6;
  private static final int GOLD = 0xFFFBBF24;
  private static final int PURPLE = 0xFFA855F7;
  private static final String[] PROVIDERS = {"Gemini", "ChatGPT", "Perplexity", "Claude", "Grok"};
  private static final int TC_CACHE_CAP = 200;
  private static final int QUERY_CAP = 500;
  private static final int RECENT_HOME_CAP = 10;
  private static final String[] TRY_WORDS = {"Rebel", "Nostalgia", "Eloquent", "Serendipity", "Resilient"};

  private SharedPreferences prefs;
  private ScrollView scrollView;
  private LinearLayout searchScreen;
  private LinearLayout savedScreen;
  private LinearLayout historyScreen;
  private LinearLayout settingsScreen;
  private LinearLayout cardScreen;
  private LinearLayout detailScreen;
  private LinearLayout resultBox;
  private LinearLayout recentSection;
  private FlowLayout recentFlow;
  private LinearLayout myWordsBox;
  private LinearLayout trySection;
  private FlowLayout tryFlow;
  private LinearLayout savedList;
  private TextView savedCount;
  private LinearLayout historyList;
  private LinearLayout settingsBox;
  private LinearLayout cardBox;
  private LinearLayout detailBox;
  private EditText input;
  private LinearLayout navSearch;
  private LinearLayout navSaved;
  private LinearLayout navHistory;
  private LinearLayout navSettings;
  private ArrayDeque<String> backStack = new ArrayDeque<String>();
  private String currentScreen = "search";
  private LinkedHashSet<String> savedSet = new LinkedHashSet<String>();
  private LinkedHashSet<String> learnedSet = new LinkedHashSet<String>();
  private List<String> historyData = new ArrayList<String>();
  private int lookupId = 0;
  private TextToSpeech tts;
  private String pendingSpeak;
  // The result currently shown in resultBox, for meaning-index sync
  // when returning from the Show card after a swap.
  private ResultData lastResultData = null;
  private int lastResultMeaningIdx = 0;
  // v5 select-to-translate popup (overlay added in onCreate).
  private FrameLayout popupOverlay;
  private TextView popupWordView;
  private TextView popupTransView;
  private TextView popupAltView;
  private LinearLayout popupSavePill;
  private TextView popupSaveText;
  private TextView popupCopyText;
  private String popupWord;
  private String popupTranslation;
  private SelTextView popupSource;
  private int popupSeq = 0;
  private String popupSl = "auto";
  private String popupTl = "fa";
  private boolean popupFa = false;
  // v5 ChatGPT option (Settings only): true while the user is
  // replacing a saved key; the key itself lives in prefs.
  private boolean chatGptEditing = false;
  private int chatGptSeq = 0;
  // Translation-card fill-in for a dictionary entry whose translation
  // is still in flight when the entry renders.
  private TextView transFaView = null;
  private String transFaWord = null;

  private interface TranslationCallback {
    void onResult(GtxParser.Result result);
  }

  private interface DictCallback {
    void onResult(DictParser.DictEntry entry);
  }

  /** One meaning of a word: part of speech, definition, example, and
      stored Persian text where the app already has it (bundled words
      and verified entries). Dictionary meanings carry no stored
      Persian, so their FA pills translate on demand instead. */
  private static class Meaning {
    String pos;
    String definition;
    String example;
    String definitionFa;
    String exampleFa;
    Meaning(String pos, String definition, String example,
        String definitionFa, String exampleFa) {
      this.pos = pos;
      this.definition = definition;
      this.example = example;
      this.definitionFa = definitionFa;
      this.exampleFa = exampleFa;
    }
  }

  /** Everything one result view needs, from any source. */
  private static class ResultData {
    String displayWord;
    String phonetic;
    String fa;
    List<String> alternatives = new ArrayList<String>();
    List<Meaning> meanings = new ArrayList<Meaning>();
    List<String> synonyms = new ArrayList<String>();
    List<String> antonyms = new ArrayList<String>();
    String hook;
    String hookAttribution;
    String cefr;
    boolean isNew;
    String scene;
    String savedKey;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prefs = getSharedPreferences("searchai", MODE_PRIVATE);
    savedSet = new LinkedHashSet<String>(
        ListCodec.decode(prefs.getString("saved", "")));
    learnedSet = new LinkedHashSet<String>(
        ListCodec.decode(prefs.getString("learned", "")));
    historyData = ListCodec.decode(prefs.getString("history", ""));

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(BG);

    ScrollView scroll = new ScrollView(this);
    scrollView = scroll;
    LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
    scroll.setLayoutParams(scrollParams);
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(16), dp(8), dp(16), dp(16));
    scroll.addView(content);
    root.addView(scroll);

    searchScreen = buildSearchScreen();
    savedScreen = buildSavedScreen();
    historyScreen = buildHistoryScreen();
    settingsScreen = new LinearLayout(this);
    settingsScreen.setOrientation(LinearLayout.VERTICAL);
    settingsBox = new LinearLayout(this);
    settingsBox.setOrientation(LinearLayout.VERTICAL);
    settingsScreen.addView(settingsBox);
    cardScreen = new LinearLayout(this);
    cardScreen.setOrientation(LinearLayout.VERTICAL);
    cardBox = new LinearLayout(this);
    cardBox.setOrientation(LinearLayout.VERTICAL);
    cardScreen.addView(cardBox);
    detailScreen = new LinearLayout(this);
    detailScreen.setOrientation(LinearLayout.VERTICAL);
    detailBox = new LinearLayout(this);
    detailBox.setOrientation(LinearLayout.VERTICAL);
    detailScreen.addView(detailBox);
    content.addView(searchScreen);
    content.addView(savedScreen);
    content.addView(historyScreen);
    content.addView(settingsScreen);
    content.addView(cardScreen);
    content.addView(detailScreen);

    LinearLayout nav = new LinearLayout(this);
    nav.setOrientation(LinearLayout.HORIZONTAL);
    nav.setBackgroundColor(CARD);
    nav.setPadding(dp(8), dp(8), dp(8), dp(8));
    navSearch = navButton("Search", "search");
    navSaved = navButton("Saved", "saved");
    navHistory = navButton("History", "history");
    navSettings = navButton("Settings", "settings");
    nav.addView(navSearch, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    nav.addView(navSaved, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    nav.addView(navHistory, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    nav.addView(navSettings, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    root.addView(nav);

    setContentView(root);
    buildSelectPopup();
    showScreen("search");
  }

  private int dp(int v) {
    float density = getResources().getDisplayMetrics().density;
    return (int) (v * density + 0.5f);
  }

  private TextView tv(String text, float size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(text);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    t.setTextColor(color);
    if (bold) t.setTypeface(null, Typeface.BOLD);
    return t;
  }

  /** v5: a selectable content TextView that reports its selection so
      the select-to-translate popup can open. Button and chip labels
      never use this (they stay plain tv(), non-selectable). */
  private SelTextView selTv(String text, float size, int color, boolean bold) {
    SelTextView t = new SelTextView(this);
    t.setText(text);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    t.setTextColor(color);
    if (bold) t.setTypeface(null, Typeface.BOLD);
    t.setTextIsSelectable(true);
    t.setSelectionListener(new SelTextView.Listener() {
      public void onTextSelected(SelTextView view, String sel) {
        onContentTextSelected(view, sel);
      }
    });
    return t;
  }

  private TextView heading(String text) {
    SelTextView t = selTv(text, 18, TEXT, true);
    t.setPadding(0, dp(20), 0, dp(8));
    return t;
  }

  private TextView label(String text) {
    SelTextView t = selTv(text, 12, SUB, true);
    t.setPadding(0, dp(14), 0, dp(4));
    return t;
  }

  private GradientDrawable rounded(int color, int radiusDp) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radiusDp));
    return d;
  }

  private GradientDrawable outlined(int strokeColor) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(0x00000000);
    d.setCornerRadius(dp(12));
    d.setStroke(dp(1), strokeColor);
    return d;
  }

  private RippleDrawable ripple(int bg, int radiusDp) {
    return new RippleDrawable(
        ColorStateList.valueOf(0x33FFFFFF), rounded(bg, radiusDp), null);
  }

  private LinearLayout pill(int bg) {
    LinearLayout p = new LinearLayout(this);
    p.setOrientation(LinearLayout.HORIZONTAL);
    p.setGravity(Gravity.CENTER_VERTICAL);
    p.setBackground(ripple(bg, 20));
    p.setPadding(dp(12), dp(8), dp(12), dp(8));
    return p;
  }

  private IconView icon(int kind, int color, int sizeDp) {
    IconView v = new IconView(this);
    v.setKind(kind);
    v.setIconColor(color);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
        dp(sizeDp), dp(sizeDp));
    v.setLayoutParams(p);
    return v;
  }

  private LinearLayout navButton(String text, final String screen) {
    LinearLayout b = new LinearLayout(this);
    b.setOrientation(LinearLayout.HORIZONTAL);
    b.setGravity(Gravity.CENTER);
    b.setBackgroundColor(CARD);
    b.setPadding(dp(4), dp(10), dp(4), dp(10));
    b.addView(tv(text, 14, SUB, false));
    b.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        navTab(screen);
      }
    });
    return b;
  }

  private LinearLayout card() {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setBackground(rounded(CARD, 16));
    c.setPadding(dp(16), dp(16), dp(16), dp(16));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    p.setMargins(0, 0, 0, dp(12));
    c.setLayoutParams(p);
    return c;
  }

  /** A small teal FA pill with a hidden Persian line below it. */
  private LinearLayout faPillRow(String faText) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setPadding(0, dp(6), 0, dp(6));
    final SelTextView faView = selTv("", 15, TEXT, false);
    faView.setText(faText);
    faView.setTextDirection(View.TEXT_DIRECTION_RTL);
    faView.setVisibility(View.GONE);
    LinearLayout pill = pill(CARD2);
    TextView faBtn = tv("FA", 12, TEAL, true);
    pill.addView(faBtn);
    pill.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        faView.setVisibility(faView.getVisibility() == View.VISIBLE
            ? View.GONE : View.VISIBLE);
      }
    });
    row.addView(pill);
    row.addView(faView);
    return row;
  }

  /** FA tap-to-reveal for fetched text: cached translation first, then
      the same keyless translation path, never an invented translation. */
  private LinearLayout lazyFaPillRow(final String englishText) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setPadding(0, dp(6), 0, dp(6));
    final SelTextView faView = selTv("", 15, TEXT, false);
    faView.setTextDirection(View.TEXT_DIRECTION_RTL);
    faView.setVisibility(View.GONE);
    LinearLayout pill = pill(CARD2);
    final TextView faBtn = tv("FA", 12, TEAL, true);
    pill.addView(faBtn);
    pill.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        if (faView.getVisibility() == View.VISIBLE) {
          faView.setVisibility(View.GONE);
          return;
        }
        String cached = getCachedTranslation(englishText, "auto", "fa");
        if (cached != null) {
          faView.setText(cached);
          faView.setVisibility(View.VISIBLE);
          return;
        }
        faBtn.setText("\u2026");
        fetchTranslation(englishText, "auto", "fa", new TranslationCallback() {
          public void onResult(GtxParser.Result r) {
            if (r.status == GtxParser.OK) {
              putCachedTranslation(englishText, "auto", "fa", r.translation);
              faView.setText(r.translation);
              faView.setVisibility(View.VISIBLE);
            }
            faBtn.setText("FA");
          }
        });
      }
    });
    row.addView(pill);
    row.addView(faView);
    return row;
  }

  /** FA pill for one meaning: stored Persian when the app has it,
      lazy translation otherwise. */
  private LinearLayout faPillFor(Meaning m) {
    if (m.definitionFa != null && m.definitionFa.length() > 0) {
      return faPillRow(m.definitionFa);
    }
    return lazyFaPillRow(m.definition);
  }

  private LinearLayout faPillForExample(Meaning m) {
    if (m.exampleFa != null && m.exampleFa.length() > 0) {
      return faPillRow(m.exampleFa);
    }
    return lazyFaPillRow(m.example);
  }

  /** "pos. definition" with the part of speech in secondary color. */
  private TextView posDefText(Meaning m, float sizeSp) {
    String pos = m.pos == null ? "" : m.pos.trim();
    TextView t;
    if (pos.length() == 0) {
      t = selTv(m.definition, sizeSp, TEXT, false);
    } else {
      String full = pos + " " + m.definition;
      SpannableString s = new SpannableString(full);
      s.setSpan(new ForegroundColorSpan(SUB), 0, pos.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      t = selTv("", sizeSp, TEXT, false);
      t.setText(s);
    }
    t.setTextIsSelectable(true);
    return t;
  }

  // ---- Navigation: a back stack replaces the old cardSource/last* fields.
  // Back never exits from a non-search screen; it returns to the previous
  // screen, and the result view is never re-rendered on Back, so revealed
  // FA text stays revealed — except when a Show card swap changed the
  // current meaning, in which case the result re-renders once so the
  // word card shows the chosen meaning. ----

  private void showScreen(String name) {
    hideSelectPopup();
    currentScreen = name;
    searchScreen.setVisibility(name.equals("search") ? View.VISIBLE : View.GONE);
    savedScreen.setVisibility(name.equals("saved") ? View.VISIBLE : View.GONE);
    historyScreen.setVisibility(name.equals("history") ? View.VISIBLE : View.GONE);
    settingsScreen.setVisibility(name.equals("settings") ? View.VISIBLE : View.GONE);
    cardScreen.setVisibility(name.equals("card") ? View.VISIBLE : View.GONE);
    detailScreen.setVisibility(name.equals("detail") ? View.VISIBLE : View.GONE);
    setNavColor(navSearch, name.equals("search") ? TEXT : SUB);
    setNavColor(navSaved, name.equals("saved") ? TEXT : SUB);
    setNavColor(navHistory, name.equals("history") ? TEXT : SUB);
    setNavColor(navSettings, name.equals("settings") ? TEXT : SUB);
    if (scrollView != null) scrollView.scrollTo(0, 0);
    if (name.equals("saved")) refreshSaved();
    if (name.equals("history")) refreshHistory();
    if (name.equals("settings")) refreshSettings();
    if (name.equals("search")) {
      if (lastResultData != null
          && getMeaningIdx(lastResultData.savedKey) != lastResultMeaningIdx) {
        lastResultMeaningIdx = getMeaningIdx(lastResultData.savedKey);
        resultBox.removeAllViews();
        renderResultStack(resultBox, lastResultData);
      }
      refreshHome();
    }
  }

  /** Set the label color of a bottom-nav button (a container whose
      first child is its label). */
  private void setNavColor(LinearLayout nav, int color) {
    if (nav.getChildCount() > 0 && nav.getChildAt(0) instanceof TextView) {
      ((TextView) nav.getChildAt(0)).setTextColor(color);
    }
  }

  private void navigate(String name) {
    if (!name.equals(currentScreen)) {
      backStack.push(currentScreen);
      showScreen(name);
    }
  }

  private void navTab(String name) {
    backStack.clear();
    showScreen(name);
  }

  @Override
  public void onBackPressed() {
    if (!backStack.isEmpty()) {
      showScreen(backStack.pop());
      return;
    }
    if (!currentScreen.equals("search")) {
      showScreen("search");
      return;
    }
    super.onBackPressed();
  }

  // ---- Storage: in-memory sets loaded once, written through on change.
  // Case-insensitive throughout, so "austere" and "Austere" never split. ----

  private void persistSaved() {
    prefs.edit().putString("saved",
        ListCodec.encode(new ArrayList<String>(savedSet))).apply();
  }

  private void persistLearned() {
    prefs.edit().putString("learned",
        ListCodec.encode(new ArrayList<String>(learnedSet))).apply();
  }

  private void persistHistory() {
    prefs.edit().putString("history", ListCodec.encode(historyData)).apply();
  }

  private boolean isSaved(String w) {
    return ListCodec.containsIgnoreCase(new ArrayList<String>(savedSet), w);
  }

  private void setSaved(String w, boolean saved) {
    removeIgnoreCase(savedSet, w);
    if (saved) savedSet.add(w);
    persistSaved();
    if (!saved) {
      // Un-saving also clears learned, so the two states never disagree.
      setLearned(w, false);
    }
  }

  private boolean isLearned(String w) {
    return ListCodec.containsIgnoreCase(new ArrayList<String>(learnedSet), w);
  }

  private void setLearned(String w, boolean learned) {
    removeIgnoreCase(learnedSet, w);
    if (learned) learnedSet.add(w);
    persistLearned();
  }

  private void removeIgnoreCase(LinkedHashSet<String> set, String v) {
    String found = null;
    for (String s : set) {
      if (s.equalsIgnoreCase(v)) {
        found = s;
        break;
      }
    }
    if (found != null) set.remove(found);
  }

  private void addToHistory(String q) {
    for (int i = historyData.size() - 1; i >= 0; i--) {
      if (historyData.get(i).equalsIgnoreCase(q)) historyData.remove(i);
    }
    historyData.add(0, q);
    while (historyData.size() > 50) historyData.remove(historyData.size() - 1);
    persistHistory();
  }

  private int getMeaningIdx(String key) {
    if (key == null) return 0;
    return prefs.getInt("meaningIdx::" + key.toLowerCase(Locale.US), 0);
  }

  private void setMeaningIdx(String key, int idx) {
    if (key == null) return;
    prefs.edit().putInt("meaningIdx::" + key.toLowerCase(Locale.US), idx).apply();
  }

  private boolean isLiked(String key) {
    if (key == null) return false;
    return prefs.getBoolean("like::" + key.toLowerCase(Locale.US), false);
  }

  private void setLiked(String key, boolean liked) {
    if (key == null) return;
    prefs.edit().putBoolean("like::" + key.toLowerCase(Locale.US), liked).apply();
  }

  // ---- Translation cache: tc::<sl>|<tl>|<query>, newest first, capped. ----

  private String tcKey(String sl, String tl, String query) {
    return "tc::" + sl + "|" + tl + "|" + query;
  }

  private String getCachedTranslation(String query, String sl, String tl) {
    return prefs.getString(tcKey(sl, tl, query), null);
  }

  private void putCachedTranslation(String query, String sl, String tl,
      String translation) {
    String key = tcKey(sl, tl, query);
    List<String> order = ListCodec.decode(prefs.getString("tc_order", ""));
    for (int i = order.size() - 1; i >= 0; i--) {
      if (order.get(i).equals(key)) order.remove(i);
    }
    order.add(0, key);
    SharedPreferences.Editor ed = prefs.edit();
    ed.putString(key, translation);
    while (order.size() > TC_CACHE_CAP) {
      String old = order.remove(order.size() - 1);
      ed.remove(old);
    }
    ed.putString("tc_order", ListCodec.encode(order));
    ed.apply();
  }

  // ---- Save/Learned sync by view tag: no re-render, FA state preserved.
  // Action pills are layouts (icon plus label), so sync walks the tree
  // for every view with the tag and updates its background and label. ----

  private void findAllByTag(ViewGroup root, String tag, List<View> out) {
    for (int i = 0; i < root.getChildCount(); i++) {
      View v = root.getChildAt(i);
      if (tag.equals(v.getTag())) out.add(v);
      if (v instanceof ViewGroup) {
        findAllByTag((ViewGroup) v, tag, out);
      }
    }
  }

  private void syncOnePill(View pill, boolean on, String onText,
      String offText, int onBg, int offBg) {
    pill.setBackground(ripple(on ? onBg : offBg, 20));
    if (pill instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) pill;
      for (int i = 0; i < g.getChildCount(); i++) {
        View c = g.getChildAt(i);
        if (c instanceof TextView) {
          ((TextView) c).setText(on ? onText : offText);
        }
      }
    }
  }

  private void syncPills(String key) {
    List<View> found = new ArrayList<View>();
    findAllByTag(resultBox, "save:" + key, found);
    findAllByTag(cardBox, "save:" + key, found);
    for (int i = 0; i < found.size(); i++) {
      syncOnePill(found.get(i), isSaved(key), "Saved", "Save", GREEN, CARD2);
    }
    found.clear();
    findAllByTag(resultBox, "learn:" + key, found);
    findAllByTag(cardBox, "learn:" + key, found);
    for (int i = 0; i < found.size(); i++) {
      syncOnePill(found.get(i), isLearned(key), "Learned", "Mark learned",
          GREEN, CARD2);
    }
  }

  /** Save pill: bookmark icon plus label, synced by tag. */
  private LinearLayout savePill(final String key) {
    final LinearLayout p = pill(isSaved(key) ? GREEN : CARD2);
    p.setTag("save:" + key);
    p.addView(icon(IconView.BOOKMARK, TEXT, 18));
    TextView t = tv(isSaved(key) ? "Saved" : "Save", 15, TEXT, false);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    tp.setMargins(dp(8), 0, 0, 0);
    t.setLayoutParams(tp);
    p.addView(t);
    p.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowSaved = !isSaved(key);
        setSaved(key, nowSaved);
        syncPills(key);
      }
    });
    return p;
  }

  /** Mark learned pill: checkmark plus label, synced by tag. */
  private LinearLayout learnPill(final String key) {
    final LinearLayout p = pill(isLearned(key) ? GREEN : CARD2);
    p.setTag("learn:" + key);
    p.addView(icon(IconView.CHECK, TEXT, 18));
    TextView t = tv(isLearned(key) ? "Learned" : "Mark learned", 15, TEXT, false);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    tp.setMargins(dp(8), 0, 0, 0);
    t.setLayoutParams(tp);
    p.addView(t);
    p.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowLearned = !isLearned(key);
        setLearned(key, nowLearned);
        syncPills(key);
      }
    });
    return p;
  }

  // ---- Result data builders: one model for bundled, dictionary,
  // verified, and translation-only results. ----

  private List<String> splitList(String s) {
    List<String> out = new ArrayList<String>();
    if (s == null) return out;
    String[] parts = s.split(", ");
    for (int i = 0; i < parts.length; i++) {
      String p = parts[i].trim();
      if (p.length() > 0) out.add(p);
    }
    return out;
  }

  private ResultData fromWord(WordsData.Word w) {
    ResultData d = new ResultData();
    d.displayWord = w.word;
    d.phonetic = w.phonetic;
    d.fa = w.definitionFa;
    d.meanings.add(new Meaning(w.pos, w.definition, w.example,
        w.definitionFa, w.exampleFa));
    d.synonyms = splitList(w.synonyms);
    d.antonyms = splitList(w.antonyms);
    d.hook = VerifiedEntries.hookFor(w.word);
    d.hookAttribution = VerifiedEntries.hookAttributionFor(w.word);
    d.cefr = WordIndex.cefrFor(w.word);
    d.isNew = false;
    d.scene = null;
    d.savedKey = w.word;
    return d;
  }

  private ResultData fromDict(DictParser.DictEntry e, String faCached) {
    ResultData d = new ResultData();
    d.displayWord = e.word;
    d.phonetic = e.phonetic;
    d.fa = faCached;
    for (int i = 0; i < e.defs.size(); i++) {
      DictParser.Def def = e.defs.get(i);
      d.meanings.add(new Meaning(def.pos, def.definition, def.example,
          null, null));
    }
    d.synonyms = new ArrayList<String>(e.synonyms);
    d.antonyms = new ArrayList<String>(e.antonyms);
    d.hook = VerifiedEntries.hookFor(e.word);
    d.hookAttribution = VerifiedEntries.hookAttributionFor(e.word);
    d.cefr = WordIndex.cefrFor(e.word);
    d.isNew = true;
    d.scene = null;
    d.savedKey = e.word;
    return d;
  }

  private ResultData fromVerified(VerifiedEntries.Entry v) {
    ResultData d = new ResultData();
    d.displayWord = v.word;
    d.phonetic = "";
    d.fa = v.fa;
    d.alternatives = new ArrayList<String>(v.alternatives);
    for (int i = 0; i < v.meanings.size(); i++) {
      DictParser.Def def = v.meanings.get(i);
      d.meanings.add(new Meaning(def.pos, def.definition, def.example,
          null, null));
    }
    d.hook = v.hook;
    d.hookAttribution = v.hookAttribution;
    d.cefr = v.cefr;
    d.isNew = true;
    d.scene = v.scene;
    d.savedKey = v.word;
    return d;
  }

  private Meaning currentMeaning(ResultData d) {
    if (d.meanings.isEmpty()) return null;
    int idx = getMeaningIdx(d.savedKey);
    if (idx < 0 || idx >= d.meanings.size()) idx = 0;
    return d.meanings.get(idx);
  }

  /** This app's own Persian for a word, for the My Words card line. */
  private String persianFor(String item) {
    WordsData.Word w = WordIndex.findExact(item);
    if (w != null && w.definitionFa != null && w.definitionFa.length() > 0) {
      return w.definitionFa;
    }
    VerifiedEntries.Entry v = VerifiedEntries.get(item);
    if (v != null) return v.fa;
    DictParser.DictEntry e = getCachedDict(item);
    if (e != null) {
      String t = getCachedTranslation(e.word, "auto", "fa");
      if (t != null) return t;
    }
    String t = prefs.getString("trans::" + item, null);
    if (t != null) return t;
    return prefs.getString("transfa::" + item, null);
  }

  // ---- Screens ----

  /** One chip: word plus an on-chip remove X when a remove action is
      given. Tapping the word searches it; tapping X removes it. */
  private LinearLayout chip(final String item, View.OnClickListener onTap,
      View.OnClickListener onRemove) {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.HORIZONTAL);
    c.setGravity(Gravity.CENTER_VERTICAL);
    c.setBackground(ripple(CARD2, 20));
    c.setPadding(dp(14), dp(8), dp(14), dp(8));
    TextView word = tv(item, 15, TEXT, false);
    word.setOnClickListener(onTap);
    c.addView(word);
    // Taps on the chip's padding land on the container, not the word,
    // so the container carries the same tap. The remove X has its own
    // listener and consumes its own taps, so it is never double-fired.
    c.setOnClickListener(onTap);
    if (onRemove != null) {
      TextView x = tv("\u00D7", 16, SUB, false);
      LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      xp.setMargins(dp(10), 0, 0, 0);
      x.setLayoutParams(xp);
      x.setOnClickListener(onRemove);
      c.addView(x);
    }
    return c;
  }

  private LinearLayout chipTap(final String item) {
    return chip(item, new View.OnClickListener() {
      public void onClick(View v) {
        input.setText(item);
        doLookup(item);
      }
    }, null);
  }

  private void refreshHome() {
    recentFlow.removeAllViews();
    if (historyData.size() > 0) {
      recentSection.setVisibility(View.VISIBLE);
      int count = historyData.size() < RECENT_HOME_CAP
          ? historyData.size() : RECENT_HOME_CAP;
      for (int i = 0; i < count; i++) {
        final String item = historyData.get(i);
        recentFlow.addView(chip(item, new View.OnClickListener() {
          public void onClick(View v) {
            input.setText(item);
            doLookup(item);
          }
        }, new View.OnClickListener() {
          public void onClick(View v) {
            for (int j = historyData.size() - 1; j >= 0; j--) {
              if (historyData.get(j).equalsIgnoreCase(item)) {
                historyData.remove(j);
              }
            }
            persistHistory();
            refreshHome();
          }
        }));
      }
    } else {
      recentSection.setVisibility(View.GONE);
    }
    myWordsBox.removeAllViews();
    myWordsBox.addView(heading("My Words"));
    myWordsBox.addView(tv(
        "Words you saved or marked learned in Search AI, kept in one archive.",
        14, SUB, false));
    List<String> mine = new ArrayList<String>();
    for (String s : savedSet) mine.add(s);
    for (String l : learnedSet) {
      boolean dup = false;
      for (String m : mine) {
        if (m.equalsIgnoreCase(l)) {
          dup = true;
          break;
        }
      }
      if (!dup) mine.add(l);
    }
    if (mine.size() == 0) {
      myWordsBox.addView(tv("No words saved yet.", 15, SUB, false));
    } else {
      for (int i = 0; i < mine.size(); i++) {
        myWordsBox.addView(myWordCard(mine.get(i)));
      }
    }
    tryFlow.removeAllViews();
    for (int i = 0; i < TRY_WORDS.length; i++) {
      tryFlow.addView(chipTap(TRY_WORDS[i]));
    }
  }

  /** Web My Words card: word bold with Persian below it on the left,
      Saved or Learned pill plus plain CEFR text on the right. */
  private LinearLayout myWordCard(final String item) {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.HORIZONTAL);
    c.setGravity(Gravity.CENTER_VERTICAL);
    c.setBackground(ripple(CARD, 16));
    c.setPadding(dp(16), dp(16), dp(16), dp(16));
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    cp.setMargins(0, 0, 0, dp(12));
    c.setLayoutParams(cp);
    LinearLayout left = new LinearLayout(this);
    left.setOrientation(LinearLayout.VERTICAL);
    left.addView(selTv(item, 18, TEXT, true));
    String fa = persianFor(item);
    if (fa != null) {
      left.addView(selTv(fa, 15, SUB, false));
    }
    c.addView(left, new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    LinearLayout right = new LinearLayout(this);
    right.setOrientation(LinearLayout.HORIZONTAL);
    right.setGravity(Gravity.CENTER_VERTICAL);
    boolean learned = isLearned(item);
    TextView p = tv(learned ? "Learned" : "Saved", 13,
        learned ? GREEN : SUB, true);
    p.setBackground(rounded(CARD2, 12));
    p.setPadding(dp(12), dp(4), dp(12), dp(4));
    right.addView(p);
    String level = WordIndex.cefrFor(item);
    if (level != null) {
      TextView lv = tv(level, 15, TEXT, true);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      lp.setMargins(dp(10), 0, 0, 0);
      lv.setLayoutParams(lp);
      right.addView(lv);
    }
    c.addView(right);
    c.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        openSavedWord(item);
      }
    });
    return c;
  }

  private LinearLayout buildSearchScreen() {
    LinearLayout screen = new LinearLayout(this);
    screen.setOrientation(LinearLayout.VERTICAL);
    // The web's "Type any word..." IS the input: large text, muted
    // placeholder, no box around it.
    input = new EditText(this);
    input.setHint("Type any word...");
    input.setHintTextColor(HEADING);
    input.setTextColor(TEXT);
    input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
    input.setTypeface(null, Typeface.BOLD);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    input.setSingleLine(true);
    input.setBackground(null);
    input.setPadding(0, dp(8), 0, dp(4));
    input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
          doLookup(input.getText().toString());
          InputMethodManager imm = (InputMethodManager)
              getSystemService(Context.INPUT_METHOD_SERVICE);
          if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
          }
          return true;
        }
        return false;
      }
    });
    screen.addView(input);

    // Look up row: small pill plus direction indicator, FA label,
    // and speaker inline in the row below the input.
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(12), 0, 0);
    LinearLayout lookupPill = pill(ACCENT);
    lookupPill.setPadding(dp(18), dp(10), dp(18), dp(10));
    lookupPill.addView(tv("Look up", 15, TEXT, false));
    lookupPill.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        doLookup(input.getText().toString());
      }
    });
    row.addView(lookupPill);
    LinearLayout dir = new LinearLayout(this);
    dir.setOrientation(LinearLayout.HORIZONTAL);
    dir.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams dirParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    dirParams.setMargins(dp(16), 0, 0, 0);
    dir.setLayoutParams(dirParams);
    dir.addView(tv("English", 14, TEXT, true));
    LinearLayout.LayoutParams swapParams = new LinearLayout.LayoutParams(
        dp(18), dp(18));
    swapParams.setMargins(dp(6), 0, dp(6), 0);
    IconView swap = icon(IconView.SWAP, BLUE, 18);
    swap.setLayoutParams(swapParams);
    dir.addView(swap);
    dir.addView(tv("Persian", 14, SUB, false));
    TextView fa = tv("FA", 12, TEXT, true);
    fa.setBackground(rounded(CARD2, 12));
    fa.setPadding(dp(10), dp(3), dp(10), dp(3));
    LinearLayout.LayoutParams faParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    faParams.setMargins(dp(8), 0, 0, 0);
    fa.setLayoutParams(faParams);
    dir.addView(fa);
    row.addView(dir);
    View spacer = new View(this);
    row.addView(spacer, new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    IconView hear = icon(IconView.SPEAKER, SUB, 22);
    hear.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        speakWord(input.getText().toString().trim());
      }
    });
    row.addView(hear);
    screen.addView(row);

    // Select-text tip line, as on the web home.
    SelTextView tip = selTv(
        "Tip: you can select any text on this page \u2014 press and hold a word "
        + "and its translation appears instantly in the popup at the bottom, "
        + "super fast.",
        13, SUB, false);
    LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    tipParams.setMargins(0, dp(12), 0, 0);
    screen.addView(tip, tipParams);

    // History and Settings as small dark pills with icons, right-aligned.
    LinearLayout pills = new LinearLayout(this);
    pills.setOrientation(LinearLayout.HORIZONTAL);
    pills.setGravity(Gravity.CENTER_VERTICAL);
    pills.setPadding(0, dp(12), 0, 0);
    View pillSpacer = new View(this);
    pills.addView(pillSpacer, new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    LinearLayout histPill = pill(CARD2);
    histPill.addView(icon(IconView.CLOCK, SUB, 18));
    TextView histText = tv("History", 14, TEXT, false);
    LinearLayout.LayoutParams histTextParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    histTextParams.setMargins(dp(6), 0, 0, 0);
    histText.setLayoutParams(histTextParams);
    histPill.addView(histText);
    histPill.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        navTab("history");
      }
    });
    pills.addView(histPill);
    LinearLayout setPill = pill(CARD2);
    LinearLayout.LayoutParams setPillParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    setPillParams.setMargins(dp(8), 0, 0, 0);
    setPill.setLayoutParams(setPillParams);
    setPill.addView(icon(IconView.GEAR, SUB, 18));
    TextView setText = tv("Settings", 14, TEXT, false);
    LinearLayout.LayoutParams setTextParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    setTextParams.setMargins(dp(6), 0, 0, 0);
    setText.setLayoutParams(setTextParams);
    setPill.addView(setText);
    setPill.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        navTab("settings");
      }
    });
    pills.addView(setPill);
    screen.addView(pills);

    resultBox = new LinearLayout(this);
    resultBox.setOrientation(LinearLayout.VERTICAL);
    resultBox.setPadding(0, dp(8), 0, 0);
    screen.addView(resultBox);

    recentSection = new LinearLayout(this);
    recentSection.setOrientation(LinearLayout.VERTICAL);
    recentSection.addView(heading("Recent searches"));
    recentFlow = new FlowLayout(this);
    recentSection.addView(recentFlow);
    screen.addView(recentSection);

    myWordsBox = new LinearLayout(this);
    myWordsBox.setOrientation(LinearLayout.VERTICAL);
    screen.addView(myWordsBox);

    trySection = new LinearLayout(this);
    trySection.setOrientation(LinearLayout.VERTICAL);
    trySection.addView(heading("Try a word"));
    tryFlow = new FlowLayout(this);
    trySection.addView(tryFlow);
    screen.addView(trySection);
    return screen;
  }

  private LinearLayout buildSavedScreen() {
    LinearLayout screen = new LinearLayout(this);
    screen.setOrientation(LinearLayout.VERTICAL);
    screen.addView(heading("Saved words"));
    savedCount = tv("", 14, SUB, false);
    screen.addView(savedCount);
    savedList = new LinearLayout(this);
    savedList.setOrientation(LinearLayout.VERTICAL);
    screen.addView(savedList);
    return screen;
  }

  private LinearLayout buildHistoryScreen() {
    LinearLayout screen = new LinearLayout(this);
    screen.setOrientation(LinearLayout.VERTICAL);
    screen.addView(heading("History"));
    historyList = new LinearLayout(this);
    historyList.setOrientation(LinearLayout.VERTICAL);
    screen.addView(historyList);
    LinearLayout clearPill = pill(CARD2);
    clearPill.addView(tv("Clear history", 15, TEXT, false));
    clearPill.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        historyData.clear();
        persistHistory();
        refreshHistory();
        refreshHome();
      }
    });
    screen.addView(clearPill);
    return screen;
  }

  private void refreshSaved() {
    savedList.removeAllViews();
    savedCount.setText(savedSet.size() + " saved words");
    if (savedSet.size() == 0) {
      savedList.addView(tv("No saved words yet. Save a word from search.", 15, SUB, false));
      return;
    }
    for (String item : savedSet) {
      final String savedItem = item;
      String labelText = item;
      if (isLearned(item)) labelText = labelText + " \u00B7 Learned";
      LinearLayout row = pill(CARD2);
      row.addView(selTv(labelText, 15, TEXT, false),
          new LinearLayout.LayoutParams(0,
              LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      LinearLayout fileBtn = pill(CARD);
      fileBtn.addView(icon(IconView.DOWNLOAD, TEXT, 16));
      TextView fileText = tv("Word file", 12, TEXT, false);
      LinearLayout.LayoutParams fileTextParams = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      fileTextParams.setMargins(dp(6), 0, 0, 0);
      fileText.setLayoutParams(fileTextParams);
      fileBtn.addView(fileText);
      fileBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          shareWordFileForSaved(savedItem);
        }
      });
      row.addView(fileBtn);
      LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      rp.setMargins(0, 0, 0, dp(8));
      row.setLayoutParams(rp);
      row.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          openSavedWord(savedItem);
        }
      });
      savedList.addView(row);
    }
  }

  private void openSavedWord(String item) {
    WordsData.Word w = WordIndex.findExact(item);
    if (w != null) {
      showResult(fromWord(w));
      return;
    }
    VerifiedEntries.Entry v = VerifiedEntries.get(item);
    if (v != null) {
      showResult(fromVerified(v));
      return;
    }
    DictParser.DictEntry e = getCachedDict(item);
    if (e != null) {
      showResult(fromDict(e, getCachedTranslation(e.word, "auto", "fa")));
      return;
    }
    String trans = prefs.getString("trans::" + item, "");
    if (trans.length() > 0) {
      showTranslationOnly(item, trans);
      return;
    }
    String transFa = prefs.getString("transfa::" + item, "");
    if (transFa.length() > 0) {
      showFaTranslation(item, transFa);
      return;
    }
    // Nothing stored for this item: show it in search without
    // re-running a lookup, so history is not polluted by the fallback.
    input.setText(item);
    navTab("search");
  }

  /** Render a full result stack on the search screen, remembering it
      for meaning-index sync when returning from the Show card. */
  private void showResult(ResultData d) {
    lastResultData = d;
    lastResultMeaningIdx = getMeaningIdx(d.savedKey);
    resultBox.removeAllViews();
    renderResultStack(resultBox, d);
    navigate("search");
  }

  private void refreshHistory() {
    historyList.removeAllViews();
    if (historyData.size() == 0) {
      historyList.addView(tv("No history yet.", 15, SUB, false));
      return;
    }
    for (int i = 0; i < historyData.size(); i++) {
      final String item = historyData.get(i);
      LinearLayout row = pill(CARD2);
      row.addView(selTv(item, 15, TEXT, false));
      LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      rp.setMargins(0, 0, 0, dp(8));
      row.setLayoutParams(rp);
      row.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          input.setText(item);
          doLookup(item);
          navTab("search");
        }
      });
      historyList.addView(row);
    }
  }

  private void refreshSettings() {
    settingsBox.removeAllViews();
    settingsBox.addView(heading("Settings"));
    final String selected = prefs.getString("provider", "");
    for (int i = 0; i < PROVIDERS.length; i++) {
      final String name = PROVIDERS[i];
      LinearLayout card = card();
      card.addView(tv(name, 16, TEXT, true));
      LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(0, dp(8), 0, dp(8));
      final boolean isOn = prefs.getBoolean("on_" + name, false);
      final TextView status = tv(isOn ? "On" : "Off", 14, SUB, false);
      row.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      Switch sw = new Switch(this);
      sw.setChecked(isOn);
      sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          prefs.edit().putBoolean("on_" + name, isChecked).apply();
          status.setText(isChecked ? "On" : "Off");
          refreshSettings();
        }
      });
      row.addView(sw);
      card.addView(row);
      boolean isSelected = selected.equals(name);
      LinearLayout usePill = pill(isSelected ? GREEN : CARD2);
      usePill.addView(tv(isSelected ? "Selected for answers" : "Use for answers",
          15, TEXT, false));
      // A provider whose switch is off cannot be picked for answers.
      usePill.setEnabled(isOn);
      if (!isOn) usePill.setAlpha(0.5f);
      usePill.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          if (!prefs.getBoolean("on_" + name, false)) return;
          prefs.edit().putString("provider", name).apply();
          refreshSettings();
        }
      });
      card.addView(usePill);
      settingsBox.addView(card);
    }
    addChatGptKeyCard();
    settingsBox.addView(tv("Search AI by SobhaN", 14, SUB, false));
    settingsBox.addView(tv("Version 5", 14, SUB, false));
    settingsBox.addView(tv("Saved words and history stay on this phone.", 14, SUB, false));
  }

  // ---- Lookup: direction comes from the query script, never a toggle. ----

  private void renderResultInline(ResultData d) {
    lastResultData = d;
    recordSearched(d.displayWord);
    lastResultMeaningIdx = getMeaningIdx(d.savedKey);
    resultBox.removeAllViews();
    renderResultStack(resultBox, d);
  }

  private void doLookup(String raw) {
    String q = raw == null ? "" : raw.replace("\n", " ").trim();
    if (q.length() == 0) {
      resultBox.removeAllViews();
      resultBox.addView(tv("Type a word first.", 15, SUB, false));
      return;
    }
    lookupId++;
    hideSelectPopup();
    final int myId = lookupId;
    if (WordIndex.isPersian(q)) {
      // Persian input: bundled reverse index first, then cache, then net.
      List<WordsData.Word> hits = WordIndex.findByFa(q, 8);
      if (hits.size() == 1) {
        WordsData.Word w = hits.get(0);
        addToHistory(w.word);
        refreshHome();
        renderResultInline(fromWord(w));
        return;
      }
      if (hits.size() > 1) {
        addToHistory(q);
        refreshHome();
        resultBox.removeAllViews();
        resultBox.addView(label("DID YOU MEAN?"));
        for (int i = 0; i < hits.size(); i++) {
          final WordsData.Word hw = hits.get(i);
          LinearLayout row = pill(CARD2);
          row.addView(tv(hw.word, 15, TEXT, false));
          row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
              input.setText(hw.word);
              doLookup(hw.word);
            }
          });
          resultBox.addView(row);
        }
        return;
      }
      String cached = getCachedTranslation(q, "fa", "en");
      if (cached != null) {
        addToHistory(q);
        refreshHome();
        resultBox.removeAllViews();
        showFaTranslation(q, cached);
        return;
      }
      addToHistory(q);
      refreshHome();
      resultBox.removeAllViews();
      final TextView waiting = tv("Translating\u2026", 15, SUB, false);
      resultBox.addView(waiting);
      final String fq = q;
      fetchTranslation(q, "fa", "en", new TranslationCallback() {
        public void onResult(GtxParser.Result r) {
          if (myId != lookupId) return;
          resultBox.removeView(waiting);
          if (r.status == GtxParser.OK) {
            putCachedTranslation(fq, "fa", "en", r.translation);
            showFaTranslation(fq, r.translation);
          } else if (r.status == GtxParser.HTTP_ERROR) {
            resultBox.addView(tv("Translation service unavailable, try again later.", 15, SUB, false));
          } else {
            resultBox.addView(tv("Needs internet for this word.", 15, SUB, false));
          }
        }
      });
    } else {
      // English input: exact match, verified entry, saved full entry,
      // cached translation, then similar words plus network (full entry
      // first). No "no offline entry" line: when a full entry arrives
      // it is the result, and the line would wrongly claim otherwise.
      WordsData.Word w = WordIndex.findExact(q);
      if (w != null) {
        addToHistory(w.word);
        refreshHome();
        renderResultInline(fromWord(w));
        return;
      }
      VerifiedEntries.Entry v = VerifiedEntries.get(q);
      if (v != null) {
        addToHistory(v.word);
        refreshHome();
        renderResultInline(fromVerified(v));
        return;
      }
      DictParser.DictEntry cachedDict = getCachedDict(q);
      if (cachedDict != null) {
        addToHistory(cachedDict.word);
        refreshHome();
        ResultData d = fromDict(cachedDict,
            getCachedTranslation(cachedDict.word, "auto", "fa"));
        renderResultInline(d);
        if (d.fa == null) fillTranslationCard(d);
        return;
      }
      String cached = getCachedTranslation(q, "auto", "fa");
      if (cached != null) {
        addToHistory(q);
        refreshHome();
        resultBox.removeAllViews();
        showTranslationOnly(q, cached);
        return;
      }
      addToHistory(q);
      refreshHome();
      resultBox.removeAllViews();
      final TextView waiting = tv("Loading full entry\u2026", 15, SUB, false);
      resultBox.addView(waiting);
      final String fq = q;
      // Full entry and translation run in parallel: the entry wins when
      // it arrives, so a repeat lookup never waits on it twice; the
      // translation is only the fallback, and when the entry wins the
      // translation is still cached under the entry's word for My Words.
      final boolean[] dictDone = new boolean[]{false};
      final boolean[] dictWon = new boolean[]{false};
      final DictParser.DictEntry[] wonEntry = new DictParser.DictEntry[]{null};
      final GtxParser.Result[] storedTrans = new GtxParser.Result[]{null};
      fetchDict(q, new DictCallback() {
        public void onResult(DictParser.DictEntry entry) {
          if (myId != lookupId) return;
          dictDone[0] = true;
          if (entry != null) {
            dictWon[0] = true;
            wonEntry[0] = entry;
            putCachedDict(entry);
            resultBox.removeView(waiting);
            String fa = null;
            if (storedTrans[0] != null && storedTrans[0].status == GtxParser.OK) {
              fa = storedTrans[0].translation;
              putCachedTranslation(entry.word, "auto", "fa", fa);
            } else {
              fa = getCachedTranslation(entry.word, "auto", "fa");
            }
            ResultData d = fromDict(entry, fa);
            renderResultInline(d);
            if (d.fa == null) fillTranslationCard(d);
          } else if (storedTrans[0] != null) {
            resultBox.removeView(waiting);
            renderTranslationResult(storedTrans[0], fq);
          } else {
            waiting.setText("Translating\u2026");
          }
        }
      });
      fetchTranslation(q, "auto", "fa", new TranslationCallback() {
        public void onResult(GtxParser.Result r) {
          if (myId != lookupId) return;
          if (dictWon[0]) {
            if (r.status == GtxParser.OK && wonEntry[0] != null) {
              putCachedTranslation(wonEntry[0].word, "auto", "fa", r.translation);
              fillTransFaView(wonEntry[0].word, r.translation);
            }
            return;
          }
          if (!dictDone[0]) {
            storedTrans[0] = r;
            return;
          }
          resultBox.removeView(waiting);
          renderTranslationResult(r, fq);
        }
      });
    }
  }

  private void fetchTranslation(final String q, final String sl, final String tl,
      final TranslationCallback cb) {
    new Thread(new Runnable() {
      public void run() {
        final GtxParser.Result r = fetchTranslationSync(q, sl, tl);
        runOnUiThread(new Runnable() {
          public void run() {
            cb.onResult(r);
          }
        });
      }
    }).start();
  }

  private GtxParser.Result fetchTranslationSync(String q, String sl, String tl) {
    String query = q;
    if (query.length() > QUERY_CAP) query = query.substring(0, QUERY_CAP);
    HttpURLConnection conn = null;
    BufferedReader reader = null;
    try {
      String enc = URLEncoder.encode(query, "UTF-8");
      String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
          + sl + "&tl=" + tl + "&dt=t&q=" + enc;
      URL url = new URL(urlStr);
      conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(8000);
      conn.setReadTimeout(8000);
      conn.setRequestProperty("User-Agent", "SearchAI/5.0");
      int code = conn.getResponseCode();
      if (code != 200) {
        return new GtxParser.Result(GtxParser.HTTP_ERROR, null);
      }
      reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) sb.append(line);
      GtxParser.Result r = GtxParser.parseGtx(sb.toString(), query);
      if (r.status == GtxParser.OK) return r;
    } catch (Exception e) {
      // Fall through to the MyMemory fallback below.
    } finally {
      try {
        if (reader != null) reader.close();
      } catch (Exception ignored) {
      }
      if (conn != null) conn.disconnect();
    }
    // MyMemory fallback (keyless). It needs real language codes, so
    // "auto" becomes "en" here; the English path is English by script.
    HttpURLConnection conn2 = null;
    BufferedReader reader2 = null;
    try {
      String mmSl = sl.equals("auto") ? "en" : sl;
      String enc = URLEncoder.encode(query, "UTF-8");
      String urlStr = "https://api.mymemory.translated.net/get?q=" + enc
          + "&langpair=" + mmSl + "|" + tl;
      URL url = new URL(urlStr);
      conn2 = (HttpURLConnection) url.openConnection();
      conn2.setConnectTimeout(8000);
      conn2.setReadTimeout(8000);
      int code = conn2.getResponseCode();
      if (code != 200) {
        return new GtxParser.Result(GtxParser.HTTP_ERROR, null);
      }
      reader2 = new BufferedReader(new InputStreamReader(conn2.getInputStream(), "UTF-8"));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader2.readLine()) != null) sb.append(line);
      String t = GtxParser.parseMyMemory(sb.toString(), query);
      if (t != null) return new GtxParser.Result(GtxParser.OK, t);
      return new GtxParser.Result(GtxParser.EMPTY, null);
    } catch (Exception e) {
      return new GtxParser.Result(GtxParser.OFFLINE, null);
    } finally {
      try {
        if (reader2 != null) reader2.close();
      } catch (Exception ignored) {
      }
      if (conn2 != null) conn2.disconnect();
    }
  }

  // ---- Dictionary full entries: keyless source, cached locally
  // so a fetched word behaves like a bundled word afterward. ----

  private String dictKey(String word) {
    return "dict::" + word.toLowerCase(Locale.US);
  }

  private DictParser.DictEntry getCachedDict(String query) {
    if (query == null) return null;
    String json = prefs.getString(dictKey(query.trim()), null);
    if (json == null) return null;
    return DictParser.fromJson(json);
  }

  private void putCachedDict(DictParser.DictEntry e) {
    String json = DictParser.toJson(e);
    if (json == null) return;
    prefs.edit().putString(dictKey(e.word), json).apply();
  }

  private void fetchDict(final String q, final DictCallback cb) {
    new Thread(new Runnable() {
      public void run() {
        final DictParser.DictEntry e = fetchDictSync(q);
        runOnUiThread(new Runnable() {
          public void run() {
            cb.onResult(e);
          }
        });
      }
    }).start();
  }

  private DictParser.DictEntry fetchDictSync(String q) {
    String query = q;
    if (query.length() > QUERY_CAP) query = query.substring(0, QUERY_CAP);
    HttpURLConnection conn = null;
    BufferedReader reader = null;
    try {
      String enc = URLEncoder.encode(query, "UTF-8");
      URL url = new URL("https://api.dictionaryapi.dev/api/v2/entries/en/" + enc);
      conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(8000);
      conn.setReadTimeout(8000);
      conn.setRequestProperty("User-Agent", "SearchAI/5.0");
      int code = conn.getResponseCode();
      if (code != 200) return null;
      reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) sb.append(line);
      return DictParser.parse(sb.toString());
    } catch (Exception e) {
      return null;
    } finally {
      try {
        if (reader != null) reader.close();
      } catch (Exception ignored) {
      }
      if (conn != null) conn.disconnect();
    }
  }

  /** Fill a rendered translation card once its Persian arrives. */
  private void fillTransFaView(String word, String translation) {
    if (transFaView != null && transFaWord != null
        && transFaWord.equalsIgnoreCase(word)) {
      transFaView.setText(translation);
      transFaView.setTextColor(TEXT);
      transFaView = null;
      transFaWord = null;
    }
  }

  /** Fetch the Persian for a rendered dictionary entry whose
      translation card is still waiting, then fill it in place. */
  private void fillTranslationCard(final ResultData d) {
    if (transFaView == null) return;
    final TextView view = transFaView;
    final String word = transFaWord;
    String cached = getCachedTranslation(d.displayWord, "auto", "fa");
    if (cached != null) {
      fillTransFaView(word, cached);
      return;
    }
    fetchTranslation(d.displayWord, "auto", "fa", new TranslationCallback() {
      public void onResult(GtxParser.Result r) {
        if (r.status == GtxParser.OK) {
          putCachedTranslation(d.displayWord, "auto", "fa", r.translation);
          if (transFaView == view) {
            view.setText(r.translation);
            view.setTextColor(TEXT);
            transFaView = null;
            transFaWord = null;
          }
        } else if (transFaView == view) {
          view.setText("Translation unavailable.");
          view.setTextColor(SUB);
          transFaView = null;
          transFaWord = null;
        }
      }
    });
  }

  private void renderTranslationResult(GtxParser.Result r, String fq) {
    if (r.status == GtxParser.OK) {
      putCachedTranslation(fq, "auto", "fa", r.translation);
      showTranslationOnly(fq, r.translation);
    } else if (r.status == GtxParser.HTTP_ERROR) {
      resultBox.addView(tv("Translation service unavailable, try again later.", 15, SUB, false));
    } else {
      resultBox.addView(tv("Needs internet for this word.", 15, SUB, false));
    }
  }

  // ---- Rendering: the web result page, section by section. ----

  private LinearLayout headerRow(String word) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView w = tv(word, 28, TEXT, true);
    row.addView(w, new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    TextView x = tv("\u00D7", 26, SUB, false);
    x.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        resultBox.removeAllViews();
        lastResultData = null;
      }
    });
    row.addView(x);
    return row;
  }

  /** Translation-only result: the word, its Persian, and Save.
      Used when no full entry exists anywhere. */
  private void showTranslationOnly(String word, String translation) {
    recordSearched(word);
    resultBox.removeAllViews();
    resultBox.addView(headerRow(word));
    LinearLayout card = card();
    TextView head = tv("TRANSLATION", 13, TEXT, true);
    card.addView(head);
    SelTextView fa = selTv(translation, 20, TEXT, false);
    fa.setTextDirection(View.TEXT_DIRECTION_RTL);
    fa.setGravity(Gravity.END);
    card.addView(fa);
    card.addView(tv("Full entry not available.", 14, SUB, false));
    resultBox.addView(card);
    final String key = word;
    LinearLayout save = pill(isSaved(key) ? GREEN : CARD2);
    save.setTag("save:" + key);
    save.addView(icon(IconView.BOOKMARK, TEXT, 18));
    TextView st = tv(isSaved(key) ? "Saved" : "Save", 15, TEXT, false);
    LinearLayout.LayoutParams stParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    stParams.setMargins(dp(8), 0, 0, 0);
    st.setLayoutParams(stParams);
    save.addView(st);
    save.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowSaved = !isSaved(key);
        setSaved(key, nowSaved);
        if (nowSaved) {
          prefs.edit().putString("trans::" + key, translation).apply();
        } else {
          prefs.edit().remove("trans::" + key).apply();
        }
        syncPills(key);
      }
    });
    resultBox.addView(save);
  }

  /** Persian-input translation result: the word, its English, Save. */
  private void showFaTranslation(String word, String translation) {
    recordSearched(word);
    resultBox.removeAllViews();
    resultBox.addView(headerRow(word));
    LinearLayout card = card();
    card.addView(tv("ENGLISH", 13, SUB, true));
    SelTextView en = selTv(translation, 20, TEXT, false);
    card.addView(en);
    resultBox.addView(card);
    final String key = word;
    LinearLayout save = pill(isSaved(key) ? GREEN : CARD2);
    save.setTag("save:" + key);
    save.addView(icon(IconView.BOOKMARK, TEXT, 18));
    TextView st = tv(isSaved(key) ? "Saved" : "Save", 15, TEXT, false);
    LinearLayout.LayoutParams stParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    stParams.setMargins(dp(8), 0, 0, 0);
    st.setLayoutParams(stParams);
    save.addView(st);
    save.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowSaved = !isSaved(key);
        setSaved(key, nowSaved);
        if (nowSaved) {
          prefs.edit().putString("transfa::" + key, translation).apply();
        } else {
          prefs.edit().remove("transfa::" + key).apply();
        }
        syncPills(key);
      }
    });
    resultBox.addView(save);
  }

  private void scrollToSection(final View section) {
    if (section == null) return;
    int y = 0;
    View v = section;
    while (v != null && v != scrollView) {
      y += v.getTop();
      if (v.getParent() instanceof View) {
        v = (View) v.getParent();
      } else {
        break;
      }
    }
    scrollView.smoothScrollTo(0, y);
  }

  private void renderResultStack(LinearLayout parent, final ResultData d) {
    transFaView = null;
    transFaWord = null;
    parent.addView(headerRow(d.displayWord));
    // Tabs row (horizontally scrollable), switching the section shown.
    final List<TextView> tabViews = new ArrayList<TextView>();
    final List<View> sections = new ArrayList<View>();
    final String[] tabNames = new String[]{"Translation", "Definitions",
        "Examples", "Synonyms", "Similar"};
    final LinearLayout tabRow = new LinearLayout(this);
    tabRow.setOrientation(LinearLayout.HORIZONTAL);
    tabRow.setGravity(Gravity.CENTER_VERTICAL);
    for (int i = 0; i < tabNames.length; i++) {
      final int idx = i;
      TextView t = tv(tabNames[i], 14, idx == 0 ? TEXT : SUB, idx == 0);
      t.setBackground(ripple(idx == 0 ? CARD2 : 0x00000000, 20));
      t.setPadding(dp(14), dp(8), dp(14), dp(8));
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      tp.setMargins(0, 0, dp(8), 0);
      t.setLayoutParams(tp);
      final TextView tabView = t;
      t.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          for (int j = 0; j < tabViews.size(); j++) {
            TextView other = tabViews.get(j);
            other.setTextColor(j == idx ? TEXT : SUB);
            other.setTypeface(null, j == idx ? Typeface.BOLD : Typeface.NORMAL);
            other.setBackground(ripple(j == idx ? CARD2 : 0x00000000, 20));
          }
          scrollToSection(sections.get(idx));
        }
      });
      tabViews.add(t);
      tabRow.addView(t);
    }
    HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
    tabsScroll.setHorizontalScrollBarEnabled(false);
    tabsScroll.addView(tabRow);
    LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    tabsParams.setMargins(0, dp(16), 0, 0);
    parent.addView(tabsScroll, tabsParams);

    // "New word" label plus CEFR level, as on the web result page.
    if (d.isNew || d.cefr != null) {
      LinearLayout nw = new LinearLayout(this);
      nw.setOrientation(LinearLayout.HORIZONTAL);
      nw.setGravity(Gravity.CENTER_VERTICAL);
      nw.setPadding(0, dp(16), 0, 0);
      if (d.isNew) {
        nw.addView(tv("New word", 14, BLUE, true));
      }
      if (d.cefr != null) {
        View spacer = new View(this);
        nw.addView(spacer, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        nw.addView(tv(d.cefr, 14, TEXT, true));
      }
      parent.addView(nw);
    }

    // TRANSLATION card: word, Persian, plus the alternatives list.
    LinearLayout transCard = card();
    LinearLayout transHead = new LinearLayout(this);
    transHead.setOrientation(LinearLayout.HORIZONTAL);
    transHead.setGravity(Gravity.CENTER_VERTICAL);
    transHead.addView(tv("TRANSLATION", 13, TEXT, true),
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    transHead.addView(tv(d.displayWord.toUpperCase(Locale.US), 13, SUB, true));
    transCard.addView(transHead);
    TextView faText;
    if (d.fa != null) {
      faText = selTv(d.fa, 20, TEXT, false);
      faText.setTextDirection(View.TEXT_DIRECTION_RTL);
      faText.setGravity(Gravity.END);
    } else {
      faText = tv("Translating\u2026", 16, SUB, false);
      transFaView = faText;
      transFaWord = d.displayWord;
    }
    transCard.addView(faText);
    if (d.alternatives.size() > 0) {
      StringBuilder alt = new StringBuilder();
      for (int i = 0; i < d.alternatives.size(); i++) {
        if (i > 0) alt.append("\u060C ");
        alt.append(d.alternatives.get(i));
      }
      SelTextView altView = selTv(alt.toString(), 14, SUB, false);
      altView.setTextDirection(View.TEXT_DIRECTION_RTL);
      altView.setGravity(Gravity.END);
      transCard.addView(altView);
    }
    parent.addView(transCard);
    sections.add(transCard);

    // "All definitions" and "Similar word" buttons.
    LinearLayout buttons = new LinearLayout(this);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setGravity(Gravity.CENTER_VERTICAL);
    buttons.setPadding(0, dp(4), 0, dp(4));
    LinearLayout allDefs = pill(CARD2);
    allDefs.addView(tv("All definitions", 14, TEXT, false));
    allDefs.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        scrollToSection(sections.get(1));
      }
    });
    buttons.addView(allDefs);
    View bSpacer = new View(this);
    buttons.addView(bSpacer, new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    LinearLayout simBtn = pill(CARD2);
    simBtn.addView(tv("Similar word \u203A", 14, TEXT, false));
    simBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        scrollToSection(sections.get(4));
      }
    });
    buttons.addView(simBtn);
    parent.addView(buttons);

    // Word card: word plus speaker, part of speech plus definition
    // plus FA pill, Save and Show card buttons with matching icons.
    LinearLayout wordCard = card();
    SelTextView wcWord = selTv(d.displayWord.toUpperCase(Locale.US), 26, TEXT, true);
    wcWord.setGravity(Gravity.CENTER);
    wordCard.addView(wcWord);
    LinearLayout speakerRow = new LinearLayout(this);
    speakerRow.setOrientation(LinearLayout.HORIZONTAL);
    speakerRow.setGravity(Gravity.CENTER);
    LinearLayout speakerPill = pill(CARD2);
    speakerPill.addView(tv(d.displayWord.toUpperCase(Locale.US), 14, TEXT, false));
    IconView spIcon = icon(IconView.SPEAKER, SUB, 18);
    LinearLayout.LayoutParams spParams = new LinearLayout.LayoutParams(
        dp(18), dp(18));
    spParams.setMargins(dp(6), 0, 0, 0);
    spIcon.setLayoutParams(spParams);
    speakerPill.addView(spIcon);
    speakerPill.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        speakWord(d.displayWord);
      }
    });
    speakerRow.addView(speakerPill);
    wordCard.addView(speakerRow);
    Meaning cur = currentMeaning(d);
    if (cur != null) {
      wordCard.addView(posDefText(cur, 16));
      wordCard.addView(faPillFor(cur));
    }
    LinearLayout actionRow = new LinearLayout(this);
    actionRow.setOrientation(LinearLayout.HORIZONTAL);
    actionRow.setPadding(0, dp(8), 0, 0);
    actionRow.addView(savePill(d.savedKey), new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    LinearLayout filePill = pill(CARD2);
    LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    fileParams.setMargins(dp(8), 0, 0, 0);
    filePill.setLayoutParams(fileParams);
    filePill.setGravity(Gravity.CENTER);
    filePill.addView(icon(IconView.DOWNLOAD, TEXT, 18));
    TextView fileText = tv("Word file", 15, TEXT, false);
    LinearLayout.LayoutParams fileTextParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    fileTextParams.setMargins(dp(8), 0, 0, 0);
    fileText.setLayoutParams(fileTextParams);
    filePill.addView(fileText);
    filePill.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        shareWordFile(d);
      }
    });
    actionRow.addView(filePill);
    LinearLayout showPill = pill(CARD2);
    LinearLayout.LayoutParams showParams = new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    showParams.setMargins(dp(8), 0, 0, 0);
    showPill.setLayoutParams(showParams);
    showPill.setGravity(Gravity.CENTER);
    showPill.addView(icon(IconView.IMAGE, TEXT, 18));
    TextView showText = tv("Show card", 15, TEXT, false);
    LinearLayout.LayoutParams showTextParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    showTextParams.setMargins(dp(8), 0, 0, 0);
    showText.setLayoutParams(showTextParams);
    showPill.addView(showText);
    showPill.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showCard(d);
      }
    });
    actionRow.addView(showPill);
    wordCard.addView(actionRow);
    parent.addView(wordCard);

    // MEMORY HOOK card, only when a hook is actually available.
    if (d.hook != null) {
      LinearLayout hookCard = card();
      hookCard.addView(tv("MEMORY HOOK", 13, GOLD, true));
      SelTextView hookText = selTv(d.hook, 15, TEXT, false);
      hookCard.addView(hookText);
      if (d.hookAttribution != null) {
        hookCard.addView(tv(d.hookAttribution, 13, SUB, false));
      }
      parent.addView(hookCard);
    }

    // Full-width Mark learned button with checkmark.
    LinearLayout learnedRow = learnPill(d.savedKey);
    LinearLayout.LayoutParams learnedParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    learnedRow.setLayoutParams(learnedParams);
    learnedRow.setGravity(Gravity.CENTER);
    parent.addView(learnedRow);
    parent.addView(tv("Saved and learned words are kept in your My Words archive.", 14, SUB, false));
    // v5: no AI note on the result page. With no key saved the page
    // stays dictionary-only with no prompt; with a key saved the
    // ChatGPT sections below replace any note.
    LinearLayout chatGptBox = new LinearLayout(this);
    chatGptBox.setOrientation(LinearLayout.VERTICAL);
    parent.addView(chatGptBox);
    populateChatGpt(chatGptBox, d);

    // Definitions section: every meaning.
    LinearLayout defSection = new LinearLayout(this);
    defSection.setOrientation(LinearLayout.VERTICAL);
    defSection.addView(heading("All definitions"));
    if (d.meanings.size() == 0) {
      defSection.addView(tv("No definitions available for this word.", 15, SUB, false));
    }
    for (int i = 0; i < d.meanings.size(); i++) {
      Meaning m = d.meanings.get(i);
      defSection.addView(posDefText(m, 16));
      defSection.addView(faPillFor(m));
    }
    parent.addView(defSection);
    sections.add(defSection);

    // Examples section: each meaning's example, or an honest note.
    LinearLayout exSection = new LinearLayout(this);
    exSection.setOrientation(LinearLayout.VERTICAL);
    exSection.addView(heading("Examples"));
    boolean anyExample = false;
    for (int i = 0; i < d.meanings.size(); i++) {
      Meaning m = d.meanings.get(i);
      if (m.example != null && m.example.length() > 0) {
        anyExample = true;
        LinearLayout inset = new LinearLayout(this);
        inset.setOrientation(LinearLayout.VERTICAL);
        inset.setBackground(rounded(CARD2, 12));
        inset.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams insetParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        insetParams.setMargins(0, 0, 0, dp(8));
        inset.setLayoutParams(insetParams);
        SelTextView ex = selTv(m.example, 15, TEXT, false);
        ex.setTypeface(null, Typeface.ITALIC);
        inset.addView(ex);
        exSection.addView(inset);
        exSection.addView(faPillForExample(m));
      }
    }
    if (!anyExample) {
      exSection.addView(tv("No example available for this word.", 15, SUB, false));
    }
    parent.addView(exSection);
    sections.add(exSection);

    // Synonyms section, plus opposites where the source gives them.
    LinearLayout synSection = new LinearLayout(this);
    synSection.setOrientation(LinearLayout.VERTICAL);
    synSection.addView(heading("Synonyms"));
    if (d.synonyms.size() > 0) {
      synonymRows(synSection, d.synonyms);
    } else {
      synSection.addView(tv("No synonyms available for this word.", 15, SUB, false));
    }
    if (d.antonyms.size() > 0) {
      synSection.addView(label("Opposite words"));
      synonymRows(synSection, d.antonyms);
    }
    parent.addView(synSection);
    sections.add(synSection);

    // Similar section: the app's own similar logic, tappable.
    LinearLayout simSection = new LinearLayout(this);
    simSection.setOrientation(LinearLayout.VERTICAL);
    simSection.addView(heading("Similar words"));
    List<WordsData.Word> similarWords = WordIndex.findSimilar(d.displayWord, 8);
    if (similarWords.size() > 0) {
      FlowLayout flow = new FlowLayout(this);
      for (int i = 0; i < similarWords.size(); i++) {
        flow.addView(chipTap(similarWords.get(i).word));
      }
      simSection.addView(flow);
    } else {
      simSection.addView(tv("No similar words found.", 15, SUB, false));
    }
    parent.addView(simSection);
    sections.add(simSection);
  }

  // ---- Show card: the web card, minus the picture panel, which the
  // key-free app cannot produce. Caption plus scene text stand in
  // place of the illustration; there is no Repaint button and no
  // "repaint it" link, because both would be dead without a picture. ----

  private void showCard(ResultData d) {
    cardBox.removeAllViews();
    int idx = getMeaningIdx(d.savedKey);
    if (idx < 0 || idx >= d.meanings.size()) idx = 0;
    renderShowCard(d, idx);
    navigate("card");
  }

  private void renderShowCard(final ResultData d, final int idx) {
    cardBox.removeAllViews();
    Meaning m = d.meanings.get(idx);
    // Text panel in place of the picture panel.
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setBackground(rounded(CARD, 16));
    panel.setPadding(dp(16), dp(16), dp(16), dp(16));
    LinearLayout topRow = new LinearLayout(this);
    topRow.setOrientation(LinearLayout.HORIZONTAL);
    topRow.setGravity(Gravity.CENTER_VERTICAL);
    topRow.addView(new View(this), new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    TextView badge = tv("\u25CB LEARNING", 13, TEXT, true);
    badge.setBackground(rounded(PURPLE, 12));
    badge.setPadding(dp(12), dp(4), dp(12), dp(4));
    topRow.addView(badge);
    panel.addView(topRow);
    TextView caption = tv("THE MEANING, IN ACTION", 13, TEAL, true);
    caption.setLetterSpacing(0.1f);
    panel.addView(caption);
    String scene = d.scene != null ? d.scene : m.definition;
    panel.addView(selTv(scene, 16, TEXT, true));
    cardBox.addView(panel);

    LinearLayout wordRow = new LinearLayout(this);
    wordRow.setOrientation(LinearLayout.HORIZONTAL);
    wordRow.setGravity(Gravity.CENTER_VERTICAL);
    wordRow.addView(tv(d.displayWord.toUpperCase(Locale.US), 28, TEXT, true),
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    IconView sp = icon(IconView.SPEAKER, SUB, 24);
    sp.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        speakWord(d.displayWord);
      }
    });
    wordRow.addView(sp);
    cardBox.addView(wordRow);
    cardBox.addView(posDefText(m, 16));
    cardBox.addView(faPillFor(m));
    if (m.example != null && m.example.length() > 0) {
      LinearLayout inset = new LinearLayout(this);
      inset.setOrientation(LinearLayout.VERTICAL);
      inset.setBackground(rounded(CARD2, 12));
      inset.setPadding(dp(14), dp(14), dp(14), dp(14));
      SelTextView ex = selTv(m.example, 15, TEXT, false);
      ex.setTypeface(null, Typeface.ITALIC);
      inset.addView(ex);
      cardBox.addView(inset);
      cardBox.addView(faPillForExample(m));
    }
    if (d.hook != null) {
      cardBox.addView(tv("Memory hook:", 14, GOLD, true));
      SelTextView hookText = selTv(d.hook, 15, TEXT, false);
      cardBox.addView(hookText);
    }
    // Action row: add, heart, share, swap, Tell Me More.
    LinearLayout actions = new LinearLayout(this);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    actions.setGravity(Gravity.CENTER_VERTICAL);
    actions.setPadding(0, dp(8), 0, 0);
    final String key = d.savedKey;
    LinearLayout add = pill(isSaved(key) ? GREEN : CARD2);
    add.setTag("save:" + key);
    add.addView(icon(IconView.ADD, TEXT, 18));
    add.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowSaved = !isSaved(key);
        setSaved(key, nowSaved);
        syncPills(key);
      }
    });
    actions.addView(add);
    final LinearLayout heart = pill(CARD2);
    heart.addView(icon(IconView.HEART, TEXT, 18));
    final TextView heartCount = tv(isLiked(key) ? "1" : "0", 14, TEXT, false);
    LinearLayout.LayoutParams heartParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    heartParams.setMargins(dp(4), 0, 0, 0);
    heartCount.setLayoutParams(heartParams);
    heart.addView(heartCount);
    heart.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean liked = !isLiked(key);
        setLiked(key, liked);
        heartCount.setText(liked ? "1" : "0");
        if (heart.getChildAt(0) instanceof IconView) {
          ((IconView) heart.getChildAt(0)).setFilled(liked);
        }
      }
    });
    if (isLiked(key) && heart.getChildAt(0) instanceof IconView) {
      ((IconView) heart.getChildAt(0)).setFilled(true);
    }
    actions.addView(heart);
    LinearLayout share = pill(CARD2);
    share.addView(icon(IconView.SHARE, TEXT, 18));
    share.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, d.displayWord + ": " + m.definition);
        startActivity(Intent.createChooser(send, "Share word"));
      }
    });
    actions.addView(share);
    if (d.meanings.size() > 1) {
      final int total = d.meanings.size();
      LinearLayout swap = pill(CARD2);
      swap.addView(icon(IconView.SWAP, BLUE, 18));
      TextView counter = tv((idx + 1) + "/" + total, 14, TEXT, false);
      LinearLayout.LayoutParams counterParams = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      counterParams.setMargins(dp(4), 0, 0, 0);
      counter.setLayoutParams(counterParams);
      swap.addView(counter);
      swap.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          int next = (idx + 1) % total;
          setMeaningIdx(key, next);
          // Re-render in place: no back-stack push for a swap.
          renderShowCard(d, next);
        }
      });
      actions.addView(swap);
    }
    LinearLayout tellMore = new LinearLayout(this);
    tellMore.setOrientation(LinearLayout.HORIZONTAL);
    tellMore.setGravity(Gravity.CENTER);
    tellMore.setBackground(outlined(BLUE));
    tellMore.setPadding(dp(12), dp(8), dp(12), dp(8));
    tellMore.addView(tv("Tell Me More", 14, BLUE, false));
    tellMore.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showDetail(d);
      }
    });
    actions.addView(tellMore, new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    cardBox.addView(actions);
    if (d.meanings.size() > 1) {
      cardBox.addView(tv("Meaning " + (idx + 1) + " of " + d.meanings.size()
          + " \u2014 tap the swap button to replace it with another meaning of "
          + d.displayWord + ".", 13, SUB, false));
    }
    LinearLayout close = pill(CARD2);
    close.addView(tv("Close", 15, TEXT, false));
    close.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    close.setLayoutParams(closeParams);
    close.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        onBackPressed();
      }
    });
    cardBox.addView(close);
  }

  /** Tell Me More: only the sections the app can actually provide.
      No overview or background content is ever invented. */
  private void showDetail(final ResultData d) {
    detailBox.removeAllViews();
    LinearLayout back = pill(CARD2);
    back.addView(tv("\u2039 Back", 15, TEXT, false));
    back.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        onBackPressed();
      }
    });
    detailBox.addView(back);
    detailBox.addView(tv(d.displayWord, 26, TEXT, true));
    detailBox.addView(heading("Definitions"));
    for (int i = 0; i < d.meanings.size(); i++) {
      Meaning m = d.meanings.get(i);
      detailBox.addView(posDefText(m, 16));
      detailBox.addView(faPillFor(m));
    }
    detailBox.addView(heading("Examples"));
    boolean anyExample = false;
    for (int i = 0; i < d.meanings.size(); i++) {
      Meaning m = d.meanings.get(i);
      if (m.example != null && m.example.length() > 0) {
        anyExample = true;
        LinearLayout inset = new LinearLayout(this);
        inset.setOrientation(LinearLayout.VERTICAL);
        inset.setBackground(rounded(CARD2, 12));
        inset.setPadding(dp(14), dp(14), dp(14), dp(14));
        SelTextView ex = selTv(m.example, 15, TEXT, false);
        ex.setTypeface(null, Typeface.ITALIC);
        inset.addView(ex);
        detailBox.addView(inset);
        detailBox.addView(faPillForExample(m));
      }
    }
    if (!anyExample) {
      detailBox.addView(tv("No example available for this word.", 15, SUB, false));
    }
    detailBox.addView(heading("Synonyms"));
    if (d.synonyms.size() > 0) {
      synonymRows(detailBox, d.synonyms);
    } else {
      detailBox.addView(tv("No synonyms available for this word.", 15, SUB, false));
    }
    if (d.antonyms.size() > 0) {
      detailBox.addView(label("Opposite words"));
      synonymRows(detailBox, d.antonyms);
    }
    if (d.hook != null) {
      detailBox.addView(heading("Memory hook"));
      detailBox.addView(tv("Memory hook:", 14, GOLD, true));
      SelTextView hookText = selTv(d.hook, 15, TEXT, false);
      detailBox.addView(hookText);
      if (d.hookAttribution != null) {
        detailBox.addView(tv(d.hookAttribution, 13, SUB, false));
      }
    }
    detailBox.addView(heading("Similar words"));
    List<WordsData.Word> similarWords = WordIndex.findSimilar(d.displayWord, 8);
    if (similarWords.size() > 0) {
      FlowLayout flow = new FlowLayout(this);
      for (int i = 0; i < similarWords.size(); i++) {
        final String sim = similarWords.get(i).word;
        flow.addView(chip(sim, new View.OnClickListener() {
          public void onClick(View v) {
            input.setText(sim);
            doLookup(sim);
            navTab("search");
          }
        }, null));
      }
      detailBox.addView(flow);
    } else {
      detailBox.addView(tv("No similar words found.", 15, SUB, false));
    }
    navigate("detail");
  }


  // ---- v5: select-to-translate popup. ----

  private void onContentTextSelected(SelTextView view, String text) {
    showSelectPopup(text, view);
  }

  private LinearLayout popupBtn(String text, int iconKind,
      View.OnClickListener l) {
    LinearLayout p = pill(CARD2);
    p.setGravity(Gravity.CENTER);
    if (iconKind >= 0) {
      p.addView(icon(iconKind, TEXT, 16));
      TextView t = tv(text, 14, TEXT, false);
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      tp.setMargins(dp(6), 0, 0, 0);
      t.setLayoutParams(tp);
      p.addView(t);
    } else {
      p.addView(tv(text, 14, TEXT, false));
    }
    p.setOnClickListener(l);
    return p;
  }

  private LinearLayout.LayoutParams popupBtnParams(boolean first,
      boolean last) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    p.setMargins(first ? 0 : dp(4), 0, last ? 0 : dp(4), 0);
    return p;
  }

  /** Builds the bottom popup overlay. The overlay sits above the
      whole activity (added via addContentView), so it works from any
      screen. Popup text is plain TextView, never SelTextView, so
      selecting popup text cannot retrigger the popup. */
  private void buildSelectPopup() {
    popupOverlay = new FrameLayout(this);
    popupOverlay.setVisibility(View.GONE);
    View dismiss = new View(this);
    dismiss.setBackgroundColor(0x00000000);
    dismiss.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        hideSelectPopup();
      }
    });
    popupOverlay.addView(dismiss, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT));
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(rounded(CARD, 20));
    card.setPadding(dp(16), dp(16), dp(16), dp(16));
    card.setClickable(true);
    FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT);
    cardParams.gravity = Gravity.BOTTOM;
    cardParams.setMargins(dp(16), dp(16), dp(16), dp(16));
    LinearLayout topRow = new LinearLayout(this);
    topRow.setOrientation(LinearLayout.HORIZONTAL);
    topRow.setGravity(Gravity.CENTER_VERTICAL);
    popupWordView = new TextView(this);
    popupWordView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
    popupWordView.setTextColor(TEXT);
    popupWordView.setTypeface(null, Typeface.BOLD);
    popupWordView.setTextIsSelectable(false);
    topRow.addView(popupWordView, new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    TextView xBtn = new TextView(this);
    xBtn.setText("\u00D7");
    xBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
    xBtn.setTextColor(TEXT);
    xBtn.setGravity(Gravity.CENTER);
    GradientDrawable oval = new GradientDrawable();
    oval.setShape(GradientDrawable.OVAL);
    oval.setColor(CARD2);
    xBtn.setBackground(oval);
    xBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
    xBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        hideSelectPopup();
      }
    });
    topRow.addView(xBtn);
    card.addView(topRow);
    popupTransView = new TextView(this);
    popupTransView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
    popupTransView.setTextColor(SUB);
    popupTransView.setTextIsSelectable(false);
    popupTransView.setPadding(0, dp(8), 0, 0);
    card.addView(popupTransView);
    popupAltView = new TextView(this);
    popupAltView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    popupAltView.setTextColor(SUB);
    popupAltView.setTextIsSelectable(false);
    popupAltView.setPadding(0, dp(4), 0, 0);
    popupAltView.setVisibility(View.GONE);
    card.addView(popupAltView);
    LinearLayout btnRow = new LinearLayout(this);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setPadding(0, dp(12), 0, 0);
    popupSavePill = popupBtn("Save", IconView.BOOKMARK,
        new View.OnClickListener() {
          public void onClick(View v) {
            popupSave();
          }
        });
    popupSaveText = (TextView) popupSavePill.getChildAt(1);
    btnRow.addView(popupSavePill, popupBtnParams(true, false));
    btnRow.addView(popupBtn("Hear", IconView.SPEAKER,
        new View.OnClickListener() {
          public void onClick(View v) {
            if (popupWord != null) speakWord(popupWord);
          }
        }), popupBtnParams(false, false));
    LinearLayout copyBtn = popupBtn("Copy", -1,
        new View.OnClickListener() {
          public void onClick(View v) {
            popupCopy();
          }
        });
    popupCopyText = (TextView) copyBtn.getChildAt(0);
    btnRow.addView(copyBtn, popupBtnParams(false, false));
    btnRow.addView(popupBtn("More", -1,
        new View.OnClickListener() {
          public void onClick(View v) {
            popupMore();
          }
        }), popupBtnParams(false, false));
    btnRow.addView(popupBtn("Close", -1,
        new View.OnClickListener() {
          public void onClick(View v) {
            hideSelectPopup();
          }
        }), popupBtnParams(false, true));
    card.addView(btnRow);
    popupOverlay.addView(card, cardParams);
    addContentView(popupOverlay, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private void hideSelectPopup() {
    if (popupOverlay != null) popupOverlay.setVisibility(View.GONE);
    if (popupSource != null) {
      popupSource.resetSelectionReport();
      try {
        popupSource.clearFocus();
      } catch (Exception ignored) {
      }
      popupSource = null;
    }
    popupWord = null;
    popupTranslation = null;
  }

  private void setPopupSaveState(boolean saved) {
    if (popupSavePill == null || popupSaveText == null) return;
    popupSavePill.setBackground(ripple(saved ? GREEN : CARD2, 20));
    popupSaveText.setText(saved ? "Saved" : "Save");
  }

  private void cachePopupTranslationForSave(String word, String translation,
      boolean toEnglish) {
    if (toEnglish) {
      prefs.edit().putString("transfa::" + word, translation).apply();
    } else {
      prefs.edit().putString("trans::" + word, translation).apply();
    }
  }

  /** Shows the popup for a selection, translating instantly via the
      fast path: bundled entry, then cache, then keyless network.
      A sequence guard drops stale responses when the user selects
      another word before an earlier fetch returns. */
  private void showSelectPopup(final String selected, SelTextView source) {
    if (popupOverlay == null) return;
    hideSelectPopup();
    popupWord = selected;
    popupSource = source;
    popupSeq++;
    final int mySeq = popupSeq;
    final boolean toEnglish = WordIndex.isPersian(selected);
    popupFa = toEnglish;
    popupSl = toEnglish ? "fa" : "auto";
    popupTl = toEnglish ? "en" : "fa";
    popupTranslation = null;
    popupWordView.setText(selected);
    popupTransView.setText("Translating\u2026");
    popupTransView.setTextColor(SUB);
    popupTransView.setGravity(toEnglish ? Gravity.START : Gravity.END);
    popupTransView.setTextDirection(toEnglish
        ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
    popupAltView.setVisibility(View.GONE);
    popupAltView.setText("");
    popupAltView.setGravity(toEnglish ? Gravity.START : Gravity.END);
    popupAltView.setTextDirection(toEnglish
        ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
    setPopupSaveState(isSaved(selected));
    popupOverlay.setVisibility(View.VISIBLE);
    String fast = null;
    if (!toEnglish) {
      WordsData.Word w = WordIndex.findExact(selected);
      if (w != null) fast = w.definitionFa;
    } else {
      List<WordsData.Word> hits = WordIndex.findByFa(selected, 8);
      if (hits.size() > 0) {
        fast = hits.get(0).word;
        if (hits.size() > 1) {
          StringBuilder alt = new StringBuilder();
          for (int i = 1; i < hits.size() && i <= 5; i++) {
            if (i > 1) alt.append(", ");
            alt.append(hits.get(i).word);
          }
          popupAltView.setText(alt.toString());
          popupAltView.setVisibility(View.VISIBLE);
        }
      }
    }
    if (fast == null) fast = getCachedTranslation(selected, popupSl, popupTl);
    if (fast != null) {
      popupTranslation = fast;
      popupTransView.setText(fast);
      popupTransView.setTextColor(TEXT);
      if (isSaved(selected)) {
        cachePopupTranslationForSave(selected, fast, toEnglish);
      }
      if (popupAltView.getVisibility() != View.VISIBLE) {
        fetchPopupAlternatives(selected, fast, mySeq);
      }
      return;
    }
    fetchTranslation(selected, popupSl, popupTl, new TranslationCallback() {
      public void onResult(final GtxParser.Result r) {
        runOnUiThread(new Runnable() {
          public void run() {
            if (mySeq != popupSeq || !selected.equals(popupWord)) return;
            if (r.status == GtxParser.OK && r.translation != null) {
              popupTranslation = r.translation;
              putCachedTranslation(selected, popupSl, popupTl, r.translation);
              popupTransView.setText(r.translation);
              popupTransView.setTextColor(TEXT);
              if (isSaved(selected)) {
                cachePopupTranslationForSave(selected, r.translation,
                    toEnglish);
              }
              fetchPopupAlternatives(selected, r.translation, mySeq);
            } else if (r.status == GtxParser.HTTP_ERROR) {
              popupTransView.setText(
                  "Translation service unavailable, try again later.");
              popupTransView.setTextColor(SUB);
            } else {
              popupTransView.setText("Needs internet for this word.");
              popupTransView.setTextColor(SUB);
            }
          }
        });
      }
    });
  }

  /** MyMemory alternatives for the popup, filled in when ready.
      Never blocks the main translation, never invents any. */
  private void fetchPopupAlternatives(final String selected,
      final String mainTranslation, final int mySeq) {
    new Thread(new Runnable() {
      public void run() {
        String body = null;
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
          String mmSl = popupSl.equals("auto") ? "en" : popupSl;
          String enc = URLEncoder.encode(selected, "UTF-8");
          String urlStr = "https://api.mymemory.translated.net/get?q=" + enc
              + "&langpair=" + mmSl + "|" + popupTl;
          URL url = new URL(urlStr);
          conn = (HttpURLConnection) url.openConnection();
          conn.setConnectTimeout(8000);
          conn.setReadTimeout(8000);
          if (conn.getResponseCode() != 200) return;
          reader = new BufferedReader(
              new InputStreamReader(conn.getInputStream(), "UTF-8"));
          StringBuilder sb = new StringBuilder();
          String line;
          while ((line = reader.readLine()) != null) sb.append(line);
          body = sb.toString();
        } catch (Exception e) {
          return;
        } finally {
          try {
            if (reader != null) reader.close();
          } catch (Exception ignored) {
          }
          if (conn != null) conn.disconnect();
        }
        final List<String> alts =
            GtxParser.parseMyMemoryMatches(body, mainTranslation, selected);
        if (alts.isEmpty()) return;
        runOnUiThread(new Runnable() {
          public void run() {
            if (mySeq != popupSeq || !selected.equals(popupWord)) return;
            if (popupAltView.getVisibility() == View.VISIBLE) return;
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < alts.size(); i++) {
              if (i > 0) joined.append(popupFa ? ", " : "\u060C ");
              joined.append(alts.get(i));
            }
            popupAltView.setText(joined.toString());
            popupAltView.setVisibility(View.VISIBLE);
          }
        });
      }
    }).start();
  }

  private void popupSave() {
    if (popupWord == null) return;
    if (!isSaved(popupWord)) {
      setSaved(popupWord, true);
      syncPills(popupWord);
      if (popupTranslation != null) {
        cachePopupTranslationForSave(popupWord, popupTranslation, popupFa);
      }
    }
    setPopupSaveState(true);
  }

  private void popupCopy() {
    if (popupWord == null) return;
    String toCopy = popupTranslation != null ? popupTranslation : popupWord;
    ClipboardManager cm =
        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm != null) {
      cm.setPrimaryClip(ClipData.newPlainText("translation", toCopy));
    }
    if (popupCopyText != null) {
      popupCopyText.setText("Copied");
      popupCopyText.postDelayed(new Runnable() {
        public void run() {
          if (popupCopyText != null) popupCopyText.setText("Copy");
        }
      }, 1500);
    }
  }

  /** More: opens AND creates the full card for the selected word and
      files it into Saved Words. The normal lookup renders the card
      (dictionary sections, plus ChatGPT sections when a key is saved,
      plus hook and scene text where actually available); a word with
      no dictionary entry shows its translation plus a plain note. */
  private void popupMore() {
    final String w = popupWord;
    final String t = popupTranslation;
    final boolean fa = popupFa;
    hideSelectPopup();
    if (w == null || w.length() == 0) return;
    input.setText(w);
    setSaved(w, true);
    syncPills(w);
    if (t != null) cachePopupTranslationForSave(w, t, fa);
    doLookup(w);
    navigate("search");
  }

  // ---- v5: per-word synonym/antonym rows with their own FA. ----

  /** One row per word: the word (tap to search) plus its own FA pill
      (tap for that word's Persian). This replaces the old chips plus
      one joined FA, which translated the whole list as a single
      phrase instead of each word. */
  private void synonymRows(LinearLayout parent, List<String> words) {
    for (int i = 0; i < words.size(); i++) {
      final String w = words.get(i);
      LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      TextView wordView = tv(w, 16, TEXT, true);
      wordView.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          chipTap(w);
        }
      });
      row.addView(wordView, new LinearLayout.LayoutParams(0,
          LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      WordsData.Word bundled = WordIndex.findExact(w);
      if (bundled != null && bundled.definitionFa != null
          && bundled.definitionFa.length() > 0) {
        row.addView(faPillRow(bundled.definitionFa));
      } else {
        row.addView(lazyFaPillRow(w));
      }
      parent.addView(row);
    }
  }

  // ---- v5: Settings-only ChatGPT API option. ----

  private String getChatGptKey() {
    String k = prefs.getString("chatgpt_key", "");
    return k == null ? "" : k;
  }

  /** The one key field in the app, in Settings only. Saved keys show
      masked (last 4 characters); Change shows an empty input, never
      the saved key; Remove clears it. The key is never logged. */
  private void addChatGptKeyCard() {
    final String savedKey = getChatGptKey();
    LinearLayout card = card();
    card.addView(tv("ChatGPT API key", 16, TEXT, true));
    card.addView(tv("Optional. When a key is saved here, word cards add "
        + "ChatGPT overview, background, memory hook, and answers. "
        + "Nothing is required, and no key is ever shown in full.",
        14, SUB, false));
    if (savedKey.length() > 0 && !chatGptEditing) {
      String last4 = savedKey.length() >= 4
          ? savedKey.substring(savedKey.length() - 4) : savedKey;
      card.addView(tv("Key saved: \u2022\u2022\u2022\u2022" + last4,
          14, TEXT, false));
      LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      LinearLayout change = pill(CARD2);
      change.addView(tv("Change", 15, TEXT, false));
      change.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          chatGptEditing = true;
          refreshSettings();
        }
      });
      row.addView(change);
      LinearLayout remove = pill(CARD2);
      remove.addView(tv("Remove", 15, TEXT, false));
      remove.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          prefs.edit().remove("chatgpt_key").apply();
          chatGptEditing = false;
          refreshSettings();
        }
      });
      row.addView(remove);
      card.addView(row);
    } else {
      final EditText keyInput = new EditText(this);
      keyInput.setHint("Paste ChatGPT API key");
      keyInput.setInputType(InputType.TYPE_CLASS_TEXT
          | InputType.TYPE_TEXT_VARIATION_PASSWORD);
      keyInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
      keyInput.setTextColor(TEXT);
      keyInput.setHintTextColor(SUB);
      keyInput.setBackgroundColor(0x00000000);
      card.addView(keyInput);
      final TextView keyNote = tv("", 13, SUB, false);
      card.addView(keyNote);
      LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      LinearLayout save = pill(CARD2);
      save.addView(tv("Save", 15, TEXT, false));
      save.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          String k = keyInput.getText().toString().trim();
          if (k.length() == 0) {
            keyNote.setText("Enter a key first.");
            return;
          }
          prefs.edit().putString("chatgpt_key", k).apply();
          chatGptEditing = false;
          refreshSettings();
        }
      });
      row.addView(save);
      if (savedKey.length() > 0) {
        LinearLayout cancel = pill(CARD2);
        cancel.addView(tv("Cancel", 15, TEXT, false));
        cancel.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v) {
            chatGptEditing = false;
            refreshSettings();
          }
        });
        row.addView(cancel);
      }
      card.addView(row);
    }
    settingsBox.addView(card);
  }

  /** ChatGPT sections for one result. No key saved: nothing renders
      and nothing prompts. Key saved: cached sections render at once;
      otherwise the real API is called and any failure falls back to
      the dictionary card with only a small status note. */
  private void populateChatGpt(final LinearLayout box, final ResultData d) {
    final String key = getChatGptKey();
    if (key.length() == 0) return;
    final int mySeq = ++chatGptSeq;
    String cacheKey = "chatgpt::" + d.displayWord.toLowerCase(Locale.US);
    String cached = prefs.getString(cacheKey, null);
    if (cached != null) {
      ChatGptSections.Sections s = ChatGptSections.parse(cached);
      if (s != null && ChatGptSections.hasAny(s)) {
        renderChatGptSections(box, d, s);
        return;
      }
      if (s == null) prefs.edit().remove(cacheKey).apply();
    }
    final String word = d.displayWord;
    new Thread(new Runnable() {
      public void run() {
        final String raw = fetchChatGptRaw(word, key);
        final ChatGptSections.Sections sections =
            raw == null ? null : ChatGptSections.parse(raw);
        runOnUiThread(new Runnable() {
          public void run() {
            if (mySeq != chatGptSeq) return;
            if (lastResultData != d) return;
            if (sections != null && ChatGptSections.hasAny(sections)) {
              prefs.edit().putString(
                  "chatgpt::" + word.toLowerCase(Locale.US), raw).apply();
              renderChatGptSections(box, d, sections);
            } else if (sections == null) {
              box.addView(tv("ChatGPT answer unavailable, showing the "
                  + "dictionary entry.", 13, SUB, false));
            }
          }
        });
      }
    }).start();
  }

  private void renderChatGptSections(LinearLayout box, ResultData d,
      ChatGptSections.Sections s) {
    if (s.overview != null) {
      LinearLayout c = card();
      c.addView(chatGptTagRow("Overview"));
      c.addView(selTv(s.overview, 15, TEXT, false));
      box.addView(c);
    }
    if (s.background != null) {
      LinearLayout c = card();
      c.addView(chatGptTagRow("Background"));
      c.addView(selTv(s.background, 15, TEXT, false));
      box.addView(c);
    }
    if (s.hook != null && d.hook == null) {
      // A verified hook already has its own MEMORY HOOK card; the
      // ChatGPT hook only fills the gap, never duplicates it.
      LinearLayout c = card();
      c.addView(chatGptTagRow("Memory hook"));
      c.addView(selTv(s.hook, 15, TEXT, false));
      box.addView(c);
    }
    if (s.answer != null) {
      LinearLayout c = card();
      c.addView(chatGptTagRow("AI answer"));
      c.addView(selTv(s.answer, 15, TEXT, false));
      box.addView(c);
    }
  }

  private LinearLayout chatGptTagRow(String title) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.addView(selTv(title, 13, TEXT, true), new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    TextView tag = tv("ChatGPT", 12, TEAL, true);
    tag.setBackground(rounded(CARD2, 12));
    tag.setPadding(dp(8), dp(2), dp(8), dp(2));
    row.addView(tag);
    return row;
  }

  /** Real ChatGPT call (JSON mode, gpt-4o-mini). Returns the message
      content (itself a JSON object), or null on any failure. The key
      is sent only in the Authorization header, never logged. */
  private String fetchChatGptRaw(String word, String key) {
    HttpURLConnection conn = null;
    BufferedReader reader = null;
    try {
      URL url = new URL("https://api.openai.com/v1/chat/completions");
      conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(25000);
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setRequestProperty("Authorization", "Bearer " + key);
      JSONObject body = new JSONObject();
      body.put("model", "gpt-4o-mini");
      body.put("temperature", 0.2);
      body.put("max_tokens", 600);
      JSONObject format = new JSONObject();
      format.put("type", "json_object");
      body.put("response_format", format);
      JSONArray messages = new JSONArray();
      JSONObject sys = new JSONObject();
      sys.put("role", "system");
      sys.put("content",
          "You write vocabulary entries. Reply with JSON only, no other text.");
      messages.put(sys);
      JSONObject usr = new JSONObject();
      usr.put("role", "user");
      usr.put("content", "For the English word '" + word + "', return a JSON "
          + "object with these keys: overview (1-2 sentences explaining what "
          + "the word means and how it is used), background (1-2 sentences on "
          + "origin or register, only if known, else empty string), hook (a "
          + "short memory hook in the shape 'Picture ... \u2014 that's " + word
          + ": <plain meaning>', else empty string), answer (a short 2-3 "
          + "sentence answer about the word). Never invent etymology or facts "
          + "you do not know; use empty strings for anything uncertain.");
      messages.put(usr);
      body.put("messages", messages);
      OutputStream out = conn.getOutputStream();
      out.write(body.toString().getBytes("UTF-8"));
      out.flush();
      out.close();
      if (conn.getResponseCode() != 200) return null;
      reader = new BufferedReader(
          new InputStreamReader(conn.getInputStream(), "UTF-8"));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) sb.append(line);
      JSONObject resp = new JSONObject(sb.toString());
      JSONArray choices = resp.optJSONArray("choices");
      if (choices == null || choices.length() == 0) return null;
      JSONObject first = choices.optJSONObject(0);
      if (first == null) return null;
      JSONObject message = first.optJSONObject("message");
      if (message == null) return null;
      String content = message.optString("content", "").trim();
      return content.length() == 0 ? null : content;
    } catch (Exception e) {
      return null;
    } finally {
      try {
        if (reader != null) reader.close();
      } catch (Exception ignored) {
      }
      if (conn != null) conn.disconnect();
    }
  }

  // ---- v5: word-file export. ----

  private void recordSearched(String word) {
    if (word == null || word.trim().length() == 0) return;
    String today =
        new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    prefs.edit().putString(
        "searched::" + word.trim().toLowerCase(Locale.US), today).apply();
  }

  private String searchedDateFor(String word) {
    String d = prefs.getString(
        "searched::" + word.trim().toLowerCase(Locale.US), null);
    if (d != null) return d;
    return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
  }

  private String sourceForWord(String word) {
    if (VerifiedEntries.get(word) != null) return "verified";
    if (WordIndex.findExact(word) != null) return "bundled";
    if (getCachedDict(word) != null) return "dictionary";
    return "translation";
  }

  private WordFileBuilder.Entry entryFromResult(ResultData d, String source) {
    WordFileBuilder.Entry e = new WordFileBuilder.Entry();
    e.word = d.displayWord;
    for (int i = 0; i < d.meanings.size(); i++) {
      Meaning m = d.meanings.get(i);
      if (m.pos != null && m.pos.length() > 0
          && !e.partsOfSpeech.contains(m.pos)) {
        e.partsOfSpeech.add(m.pos);
      }
      e.definitions.add(
          new WordFileBuilder.Def(m.pos, m.definition, m.example));
    }
    e.synonyms.addAll(d.synonyms);
    e.antonyms.addAll(d.antonyms);
    e.persian = d.fa;
    e.persianAlternatives.addAll(d.alternatives);
    e.phonetic = d.phonetic;
    e.memoryHook = d.hook;
    e.scene = d.scene;
    e.cefr = d.cefr;
    e.source = source;
    e.dateSearched = searchedDateFor(d.displayWord);
    return e;
  }

  /** Word card button: shares the file for the shown result. A result
      with no definitions keeps only its translation (if any) plus a
      plain note, never a fake entry. */
  private void shareWordFile(ResultData d) {
    if (d == null) return;
    WordFileBuilder.Entry e =
        entryFromResult(d, sourceForWord(d.displayWord));
    if (e.definitions.isEmpty()) {
      e.note = "Full entry not available.";
    }
    writeAndShareWordFile(e);
  }

  /** Saved Words entry button: resolves exactly like openSavedWord
      (bundled, verified, cached dict, then translation-only). */
  private void shareWordFileForSaved(String key) {
    WordsData.Word bundled = WordIndex.findExact(key);
    if (bundled != null) {
      shareWordFile(fromWord(bundled));
      return;
    }
    VerifiedEntries.Entry v = VerifiedEntries.get(key);
    if (v != null) {
      shareWordFile(fromVerified(v));
      return;
    }
    DictParser.DictEntry dict = getCachedDict(key);
    if (dict != null) {
      shareWordFile(fromDict(dict,
          getCachedTranslation(dict.word, "auto", "fa")));
      return;
    }
    String trans = prefs.getString("trans::" + key, "");
    if (trans.length() > 0) {
      WordFileBuilder.Entry e = new WordFileBuilder.Entry();
      e.word = key;
      e.persian = trans;
      e.source = "translation";
      e.dateSearched = searchedDateFor(key);
      e.note = "Full entry not available.";
      writeAndShareWordFile(e);
      return;
    }
    String transFa = prefs.getString("transfa::" + key, "");
    if (transFa.length() > 0) {
      WordFileBuilder.Entry e = new WordFileBuilder.Entry();
      e.word = key;
      e.english = transFa;
      e.source = "translation";
      e.dateSearched = searchedDateFor(key);
      e.note = "Full entry not available.";
      writeAndShareWordFile(e);
      return;
    }
    Toast.makeText(this, "Word file is not available for this word.",
        Toast.LENGTH_SHORT).show();
  }

  /** Writes one JSON file to the app cache and opens the share sheet
      for it. No storage permission is needed: the file is served by
      the app's own provider with a per-URI read grant. */
  private void writeAndShareWordFile(WordFileBuilder.Entry e) {
    try {
      String filename = WordFileBuilder.filenameFor(e.word);
      File dir = WordFileProvider.wordFilesDir(this);
      File file = new File(dir, filename);
      FileOutputStream out = new FileOutputStream(file);
      out.write(WordFileBuilder.buildJson(e).getBytes("UTF-8"));
      out.close();
      Uri uri = new Uri.Builder().scheme("content")
          .authority(WordFileProvider.AUTHORITY)
          .appendPath(filename).build();
      Intent send = new Intent(Intent.ACTION_SEND);
      send.setType("application/json");
      send.putExtra(Intent.EXTRA_STREAM, uri);
      send.putExtra(Intent.EXTRA_SUBJECT, filename);
      send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(send, "Share word file"));
    } catch (Exception ex) {
      Toast.makeText(this, "Could not create the word file.",
          Toast.LENGTH_SHORT).show();
    }
  }

  // ---- Text-to-speech: picks Persian voice for Persian text,
  // English voice otherwise, and never starts a second utterance
  // while one is still speaking. ----

  private void speakWord(String word) {
    if (word == null || word.trim().length() == 0) return;
    if (tts != null && tts.isSpeaking()) return;
    pendingSpeak = word.trim();
    if (tts == null) {
      tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
        public void onInit(int status) {
          if (status == TextToSpeech.SUCCESS) {
            speakNow();
          }
        }
      });
      return;
    }
    speakNow();
  }

  private void speakNow() {
    if (tts == null || pendingSpeak == null) return;
    String word = pendingSpeak;
    pendingSpeak = null;
    java.util.Locale locale = WordIndex.isPersian(word)
        ? new java.util.Locale("fa", "IR") : java.util.Locale.US;
    tts.setLanguage(locale);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "speakWord");
    } else {
      tts.speak(word, TextToSpeech.QUEUE_FLUSH, null);
    }
  }

  @Override
  protected void onDestroy() {
    if (tts != null) {
      tts.stop();
      tts.shutdown();
      tts = null;
    }
    super.onDestroy();
  }
}
