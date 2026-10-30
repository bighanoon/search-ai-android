package com.sobhan.searchai;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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

/** Search AI v7: v6 plus the redesigned Show card (one clean meaning,
    picture panel with status badge and play button, no All-information
    list) and the redesigned Tell Me More bottom sheet (word plus
    speaker, definition, All definitions and Wikipedia pills, gradient
    divider, Overview / In Action / How Common / Background / Related
    cards, empty sections omitted, never faked), and the result page
    no longer auto-shows the long inline definitions block.

    Theme, buttons, icons, chips, and information all follow the
    user's web screenshots (home, result page, Show card). Pictures
    are original illustrations generated for this app and bundled as
    drawables; new pictures arrive later through "Updating cards",
    never a fake picture, and there is no Repaint button (the app
    cannot generate pictures on the phone). */
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
  // v7: pink end of the gradient border and divider (reference card
  // and detail sheet use a pink-to-blue gradient).
  private static final int PINK = 0xFFEC4899;
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
  // v10: the Show card is a centered popup overlay (not a screen), so
  // the card renders into cardPopupBox inside cardOverlay on demand.
  // cardScreen/cardBox are legacy after v10 and stay unused.
  private FrameLayout cardOverlay = null;
  private LinearLayout cardPopupBox = null;
  // v10: result-page extras, hidden while a result is shown, shown on
  // the home page: the tip line, the History/Settings pills row, and
  // the Updating cards row. (Named homePillsRow so it cannot shadow
  // the detail sheet's own local pillsRow.)
  private SelTextView tipView = null;
  private LinearLayout homePillsRow = null;
  private LinearLayout updateRow = null;
  // v7: Tell Me More is a bottom-sheet overlay, not a screen, so the
  // detail is built into detailContent inside detailOverlay on
  // demand; detailData tracks which word the open sheet shows, and
  // detailSeq guards the background ChatGPT fetch against a stale
  // rebuild after the sheet closed or moved to another word.
  private FrameLayout detailOverlay = null;
  private LinearLayout detailContent = null;
  private ResultData detailData = null;
  private int detailSeq = 0;
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
  // v6 "Updating cards" bottom option: downloads new or updated
  // pictures from the hosted pictures folder. Bundled pictures stay
  // as the offline fallback; downloaded pictures win when present.
  // Base URL: the GitHub pictures folder. The repo is currently
  // private, so until the folder is publicly reachable this option
  // reports that honestly instead of faking a download.
  private static final String UPDATES_BASE_URL =
      "https://raw.githubusercontent.com/bighanoon/search-ai-android/main/pictures/";
  private static final int BUNDLED_PICTURE_VERSION = 1;
  private TextView updatingCardsResult = null;

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
    content.addView(searchScreen);
    content.addView(savedScreen);
    content.addView(historyScreen);
    content.addView(settingsScreen);
    content.addView(cardScreen);

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

  /** v6: a small teal FA text link with a hidden Persian line below
      it. The link is plain tappable text, never a full-width bar:
      the v5 pill(CARD2) container rendered as a large dark bar and
      the user asked for a small text link instead. Tapping reveals
      only this section's Persian. */
  private TextView faLink(final View toggleTarget) {
    TextView link = tv("FA", 12, TEAL, true);
    link.setPadding(dp(8), dp(2), dp(8), dp(2));
    link.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        toggleTarget.setVisibility(toggleTarget.getVisibility() == View.VISIBLE
            ? View.GONE : View.VISIBLE);
      }
    });
    return link;
  }

  /** A small teal FA text link with a hidden Persian line below it. */
  private LinearLayout faPillRow(String faText) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setPadding(0, dp(4), 0, dp(4));
    final SelTextView faView = selTv("", 15, TEXT, false);
    faView.setText(faText);
    faView.setTextDirection(View.TEXT_DIRECTION_RTL);
    faView.setVisibility(View.GONE);
    row.addView(faLink(faView));
    row.addView(faView);
    return row;
  }

  /** FA tap-to-reveal for fetched text: cached translation first, then
      the same keyless translation path, never an invented translation.
      v6: the control is the same small FA text link as faPillRow.
      v7: the handler lives in wireLazyFa, shared with the new card
      and detail FA links. */
  private LinearLayout lazyFaPillRow(final String englishText) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setPadding(0, dp(4), 0, dp(4));
    final SelTextView faView = selTv("", 15, TEXT, false);
    faView.setTextDirection(View.TEXT_DIRECTION_RTL);
    faView.setVisibility(View.GONE);
    final TextView faBtn = faLink(faView);
    // The link toggles visibility, but a lazy row must fetch before
    // it can show anything, so it carries the shared lazy handler
    // instead of the plain toggle.
    wireLazyFa(faBtn, faView, englishText);
    row.addView(faBtn);
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
    hideDetailSheet();
    hideCardPopup();
    currentScreen = name;
    searchScreen.setVisibility(name.equals("search") ? View.VISIBLE : View.GONE);
    savedScreen.setVisibility(name.equals("saved") ? View.VISIBLE : View.GONE);
    historyScreen.setVisibility(name.equals("history") ? View.VISIBLE : View.GONE);
    settingsScreen.setVisibility(name.equals("settings") ? View.VISIBLE : View.GONE);
    cardScreen.setVisibility(name.equals("card") ? View.VISIBLE : View.GONE);
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
    // v7: an open Tell Me More sheet closes first, like Back from
    // any other screen; only then does the back stack pop.
    if (detailOverlay != null
        && detailOverlay.getVisibility() == View.VISIBLE) {
      hideDetailSheet();
      return;
    }
    // v10: an open Show card popup closes next.
    if (cardOverlay != null
        && cardOverlay.getVisibility() == View.VISIBLE) {
      hideCardPopup();
      return;
    }
    // v10: with no X close control, Back on a result clears the
    // result and restores the home sections instead of exiting the
    // app, so the result page always has a way back to home.
    if (currentScreen.equals("search") && resultBox != null
        && resultBox.getChildCount() > 0) {
      resultBox.removeAllViews();
      lastResultData = null;
      updateResultSections();
      return;
    }
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
    if (cardPopupBox != null) {
      findAllByTag(cardPopupBox, "save:" + key, found);
    }
    for (int i = 0; i < found.size(); i++) {
      syncOnePill(found.get(i), isSaved(key), "Saved", "Save", GREEN, CARD2);
    }
    found.clear();
    findAllByTag(resultBox, "learn:" + key, found);
    if (cardPopupBox != null) {
      findAllByTag(cardPopupBox, "learn:" + key, found);
    }
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
    updateResultSections();
  }

  /** v8: the result page never shows the My Words or Try a word
      sections. The search screen is one scrolling page (resultBox,
      then Recent searches, My Words, Try a word, Updating cards), so
      "result page" means resultBox currently holds content and
      "home page" means it is empty. Recent searches stay on both.
      v10: the result page also hides the tip line, the History and
      Settings pills, and the Updating cards row; all of them come
      back on the home page. The Updating cards result line stays
      visible only when it actually has text, as in v9. This only
      changes visibility; saved-word data is never touched here. */
  private void updateResultSections() {
    if (myWordsBox == null || trySection == null || resultBox == null) {
      return;
    }
    boolean shown = ResultPageSections.resultShown(resultBox.getChildCount());
    int vis = ResultPageSections.sectionsVisible(shown) ? View.VISIBLE : View.GONE;
    myWordsBox.setVisibility(vis);
    trySection.setVisibility(vis);
    int extraVis = ResultPageSections.extrasVisible(shown) ? View.VISIBLE : View.GONE;
    if (tipView != null) tipView.setVisibility(extraVis);
    if (homePillsRow != null) homePillsRow.setVisibility(extraVis);
    if (updateRow != null) updateRow.setVisibility(extraVis);
    if (updatingCardsResult != null) {
      if (shown) {
        updatingCardsResult.setVisibility(View.GONE);
      } else {
        CharSequence t = updatingCardsResult.getText();
        updatingCardsResult.setVisibility(t != null && t.length() > 0 ? View.VISIBLE : View.GONE);
      }
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
    // v10: kept in a field so it can hide while a result is shown.
    tipView = selTv(
        "Tip: you can select any text on this page \u2014 press and hold a word "
        + "and its translation appears instantly in the popup at the bottom, "
        + "super fast.",
        13, SUB, false);
    LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    tipParams.setMargins(0, dp(12), 0, 0);
    screen.addView(tipView, tipParams);

    // History and Settings as small dark pills with icons, right-aligned.
    homePillsRow = new LinearLayout(this);
    homePillsRow.setOrientation(LinearLayout.HORIZONTAL);
    homePillsRow.setGravity(Gravity.CENTER_VERTICAL);
    homePillsRow.setPadding(0, dp(12), 0, 0);
    View pillSpacer = new View(this);
    homePillsRow.addView(pillSpacer, new LinearLayout.LayoutParams(0,
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
    homePillsRow.addView(histPill);
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
    homePillsRow.addView(setPill);
    screen.addView(homePillsRow);

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
    // v6 "Updating cards": a small, beautiful bottom option. Tapping
    // it downloads new or updated pictures from the hosted folder
    // and the cards use them; bundled pictures stay as the offline
    // fallback. It is deliberately small (one centered pill plus a
    // one-line result), not a banner and not a Settings button.
    updateRow = new LinearLayout(this);
    updateRow.setOrientation(LinearLayout.HORIZONTAL);
    updateRow.setGravity(Gravity.CENTER);
    updateRow.setPadding(0, dp(16), 0, dp(8));
    LinearLayout updatePill = pill(CARD2);
    updatePill.addView(icon(IconView.DOWNLOAD, TEAL, 16));
    TextView updateText = tv("Updating cards", 13, TEXT, false);
    LinearLayout.LayoutParams updateTextParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    updateTextParams.setMargins(dp(6), 0, 0, 0);
    updateText.setLayoutParams(updateTextParams);
    updatePill.addView(updateText);
    updatePill.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        runUpdatingCards();
      }
    });
    updateRow.addView(updatePill);
    screen.addView(updateRow);
    updatingCardsResult = tv("", 13, SUB, false);
    updatingCardsResult.setGravity(Gravity.CENTER);
    updatingCardsResult.setVisibility(View.GONE);
    screen.addView(updatingCardsResult);
    return screen;
  }

  /** v6 "Updating cards" download. Fetches the hosted manifest, then
      downloads only pictures that are new or newer than what the
      phone already has (a bundled picture counts as version 1, so a
      first tap with an unchanged folder downloads nothing). The
      result is one small line: how many pictures loaded, that the
      cards are up to date, or honestly that the folder could not be
      reached. Nothing is ever faked as downloaded. */
  private void runUpdatingCards() {
    if (updatingCardsResult != null) {
      updatingCardsResult.setText("Checking for new pictures\u2026");
      updatingCardsResult.setVisibility(View.VISIBLE);
    }
    new Thread(new Runnable() {
      public void run() {
        final String result = fetchPictureUpdates();
        runOnUiThread(new Runnable() {
          public void run() {
            if (updatingCardsResult != null) {
              updatingCardsResult.setText(result);
              updatingCardsResult.setVisibility(View.VISIBLE);
            }
          }
        });
      }
    }).start();
  }

  private String fetchPictureUpdates() {
    HttpURLConnection conn = null;
    BufferedReader reader = null;
    try {
      URL url = new URL(UPDATES_BASE_URL + "pictures.json");
      conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(8000);
      conn.setReadTimeout(8000);
      conn.setRequestProperty("User-Agent", "SearchAI/6.0");
      if (conn.getResponseCode() != 200) {
        return "Couldn't reach the pictures folder yet.";
      }
      reader = new BufferedReader(
          new InputStreamReader(conn.getInputStream(), "UTF-8"));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) sb.append(line);
      List<UpdatesManifest.Picture> pics =
          UpdatesManifest.parse(sb.toString());
      File dir = new File(getFilesDir(), "pictures");
      if (!dir.exists()) dir.mkdirs();
      int loaded = 0;
      for (int i = 0; i < pics.size(); i++) {
        UpdatesManifest.Picture pic = pics.get(i);
        int local = pictureVersion(pic.word);
        if (local == pic.version) continue;
        if (local == BUNDLED_PICTURE_VERSION
            && pic.version == BUNDLED_PICTURE_VERSION) {
          continue;
        }
        if (!downloadPictureFile(pic.file, new File(dir, pic.file))) {
          continue;
        }
        prefs.edit().putInt("picver::" + pic.word.toLowerCase(Locale.US),
            pic.version).apply();
        loaded++;
      }
      if (loaded > 0) {
        return loaded + " new pictures loaded.";
      }
      return "Cards are up to date.";
    } catch (Exception e) {
      return "Couldn't reach the pictures folder yet.";
    } finally {
      try {
        if (reader != null) reader.close();
      } catch (Exception ignored) {
      }
      if (conn != null) conn.disconnect();
    }
  }

  /** The picture version the phone already has for a word: the
      downloaded version, else the bundled version when a bundled
      drawable exists, else 0 (no picture yet). */
  private int pictureVersion(String word) {
    int downloaded = prefs.getInt("picver::" + word.toLowerCase(Locale.US), -1);
    if (downloaded >= 0) return downloaded;
    String resName = PictureNames.drawableName(word);
    if (resName.length() == 0) return 0;
    int res = getResources().getIdentifier(resName, "drawable",
        getPackageName());
    return res != 0 ? BUNDLED_PICTURE_VERSION : 0;
  }

  private boolean downloadPictureFile(String fileName, File dest) {
    HttpURLConnection conn = null;
    try {
      URL url = new URL(UPDATES_BASE_URL + fileName);
      conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(8000);
      conn.setReadTimeout(15000);
      conn.setRequestProperty("User-Agent", "SearchAI/6.0");
      if (conn.getResponseCode() != 200) return false;
      java.io.InputStream in = conn.getInputStream();
      FileOutputStream out = new FileOutputStream(dest);
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
      out.close();
      in.close();
      return dest.isFile() && dest.length() > 0;
    } catch (Exception e) {
      return false;
    } finally {
      if (conn != null) conn.disconnect();
    }
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
      // v6: no per-word Word file button on Saved words entries either.
      // The single bulk button below the list covers all saved words.
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
    // v6 bulk word file: ONE button below the list, saving ALL saved
    // words at once in ONE JSON file. Hidden when nothing is saved.
    LinearLayout bulkBtn = pill(CARD2);
    bulkBtn.setGravity(Gravity.CENTER);
    bulkBtn.addView(icon(IconView.DOWNLOAD, TEXT, 18));
    TextView bulkText = tv("Download all words", 15, TEXT, false);
    LinearLayout.LayoutParams bulkTextParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    bulkTextParams.setMargins(dp(8), 0, 0, 0);
    bulkText.setLayoutParams(bulkTextParams);
    bulkBtn.addView(bulkText);
    bulkBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        shareAllSavedWords();
      }
    });
    LinearLayout.LayoutParams bulkParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    bulkParams.setMargins(0, dp(12), 0, 0);
    bulkBtn.setLayoutParams(bulkParams);
    savedList.addView(bulkBtn);
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
    if (hasDictCacheEntry(item)) {
      // v6: stale dict cache for a saved word: re-run the lookup so
      // the fresh merged entry replaces it (doLookup re-fetches and
      // re-caches).
      input.setText(item);
      doLookup(item);
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
    updateResultSections();
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
    updateResultSections();
  }

  private void doLookup(String raw) {
    String q = raw == null ? "" : raw.replace("\n", " ").trim();
    if (q.length() == 0) {
      resultBox.removeAllViews();
      resultBox.addView(tv("Type a word first.", 15, SUB, false));
      // v8: the empty-input note is not a word result, so the home
      // sections stay visible.
      // v10: the tip line, pills, and Updating cards are extras too,
      // so they come back here as well.
      if (myWordsBox != null) myWordsBox.setVisibility(View.VISIBLE);
      if (trySection != null) trySection.setVisibility(View.VISIBLE);
      if (tipView != null) tipView.setVisibility(View.VISIBLE);
      if (homePillsRow != null) homePillsRow.setVisibility(View.VISIBLE);
      if (updateRow != null) updateRow.setVisibility(View.VISIBLE);
      if (updatingCardsResult != null) {
        CharSequence t = updatingCardsResult.getText();
        updatingCardsResult.setVisibility(t != null && t.length() > 0 ? View.VISIBLE : View.GONE);
      }
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
        updateResultSections();
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
      updateResultSections();
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
          updateResultSections();
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
      // v6: a stale (pre-v6, possibly first-entry-only) dict cache must
      // not fall through to the translation-only shortcut: the word
      // needs a fresh network fetch so its full merged entry replaces
      // the stale one. hasDictCacheEntry is true only when raw cache
      // text exists but fromJson rejected it as stale.
      boolean staleDict = hasDictCacheEntry(q);
      String cached = staleDict ? null
          : getCachedTranslation(q, "auto", "fa");
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
      updateResultSections();
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
      conn.setRequestProperty("User-Agent", "SearchAI/6.0");
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

  /** True when raw dict cache text exists for a word, even when it
      is stale and fromJson rejects it. Used to force a re-fetch
      instead of serving a translation-only fallback for a word the
      app once cached incompletely. */
  private boolean hasDictCacheEntry(String query) {
    if (query == null) return false;
    return prefs.getString(dictKey(query.trim()), null) != null;
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
      conn.setRequestProperty("User-Agent", "SearchAI/6.0");
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
    updateResultSections();
  }

  // ---- Rendering: the web result page, section by section. ----

  // v10: the result block starts directly with the tabs row. The
  // small X close control is removed entirely; system Back on a
  // result clears the result and restores the home sections (see
  // onBackPressed), so the result page is never left without a way
  // back to home. Every other result-page element is unchanged.

  /** Translation-only result: the word, its Persian, and Save.
      Used when no full entry exists anywhere. */
  private void showTranslationOnly(String word, String translation) {
    recordSearched(word);
    resultBox.removeAllViews();
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
    updateResultSections();
  }

  /** Persian-input translation result: the word, its English, Save. */
  private void showFaTranslation(String word, String translation) {
    recordSearched(word);
    resultBox.removeAllViews();
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
    updateResultSections();
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
          if (idx == 0) {
            scrollToSection(sections.get(0));
          } else {
            // v7: Definitions, Examples, Synonyms, and Similar no
            // longer render inline on the result page; they live in
            // the Tell Me More detail, so those tabs open it.
            showDetail(d);
          }
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

    // "All definitions" and "Similar word" buttons: both open the
    // Tell Me More detail in v7 (the full information no longer
    // renders inline below the card).
    LinearLayout buttons = new LinearLayout(this);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setGravity(Gravity.CENTER_VERTICAL);
    buttons.setPadding(0, dp(4), 0, dp(4));
    LinearLayout allDefs = pill(CARD2);
    allDefs.addView(tv("All definitions", 14, TEXT, false));
    allDefs.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showDetail(d);
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
        showDetail(d);
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
    // v6: no per-word Word file button on the result card (user: "Hide
    // it, it shouldn't be there"). The bulk button on the Saved words
    // page is the only word-file control now.
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

    // v7: the inline Definitions / Examples / Synonyms / Similar
    // sections are gone from the result page. The full information
    // lives only in the Tell Me More detail (opened from the tabs,
    // the "All definitions" button, or the Show card's Tell Me More).
  }

  // ---- Show card: the web card, minus the picture panel, which the
  // key-free app cannot produce. Caption plus scene text stand in
  // place of the illustration; there is no Repaint button and no
  // "repaint it" link, because both would be dead without a picture. ----

  /** v6 picture resolution: a picture downloaded through "Updating
      cards" wins over the bundled drawable; otherwise the bundled
      drawable; otherwise null (no picture image, never a fake). */
  private Bitmap pictureBitmapFor(String word) {
    try {
      File dir = new File(getFilesDir(), "pictures");
      File f = new File(dir, PictureNames.fileName(word));
      if (f.isFile()) {
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (b != null) return b;
      }
    } catch (Exception ignored) {
    }
    String resName = PictureNames.drawableName(word);
    if (resName.length() == 0) return null;
    int res = getResources().getIdentifier(resName, "drawable",
        getPackageName());
    if (res == 0) return null;
    try {
      return BitmapFactory.decodeResource(getResources(), res);
    } catch (Exception ignored) {
      return null;
    }
  }

  /** Scene sentence for the Show card picture panel: the verified
      scene first, then the stored scene for the pictured word, else
      null (the panel then shows the picture without a sentence). */
  private String sceneTextFor(ResultData d) {
    if (d.scene != null && d.scene.length() > 0) return d.scene;
    return SceneTexts.forWord(d.displayWord);
  }

  private void showCard(ResultData d) {
    // v10: the card is a centered popup overlay, not a screen, so
    // opening it does not push the back stack; Back closes the popup
    // (see onBackPressed) and a second Back clears the result.
    if (cardOverlay == null) buildCardOverlay();
    cardPopupBox.removeAllViews();
    // v7: the redesigned card always shows the first meaning; every
    // other meaning lives in Tell Me More, so there is no swap state
    // that could strand the card on a meaning with no way back.
    renderShowCard(d, 0);
    cardOverlay.setVisibility(View.VISIBLE);
    cardOverlay.bringToFront();
  }

  private void renderShowCard(final ResultData d, final int idx) {
    // v10: renders into the centered popup box; the picture panel
    // stays on top when a picture exists (see below), and a word
    // with no picture gets no panel, never a fake picture.
    cardPopupBox.removeAllViews();
    Meaning m = d.meanings.get(idx);
    // v7 redesigned card: one enclosing dark card. Picture panel on
    // top (edge to edge, rounded top corners only), status badge
    // top-right, play button bottom-right; below it the word plus
    // speaker plus "..." menu, ONE definition with a small FA link,
    // ONE example inset with its FA link, then the action row. No
    // caption, no scene text, no hook, no All-information list.
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(rounded(CARD, 16));
    cardPopupBox.addView(card, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT));
    Bitmap pic = pictureBitmapFor(d.displayWord);
    if (pic != null) {
      FrameLayout picArea = new FrameLayout(this);
      ImageView img = new ImageView(this);
      img.setImageBitmap(topRoundedBitmap(pic, 16));
      img.setScaleType(ImageView.ScaleType.CENTER_CROP);
      picArea.addView(img, new FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT, dp(280)));
      TextView badge = tv(cardStatus(d), 13, TEXT, true);
      GradientDrawable badgeBg = new GradientDrawable();
      badgeBg.setColor(0xB3000000);
      badgeBg.setCornerRadius(dp(12));
      badge.setBackground(badgeBg);
      badge.setPadding(dp(12), dp(6), dp(12), dp(6));
      FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.WRAP_CONTENT,
          FrameLayout.LayoutParams.WRAP_CONTENT);
      badgeParams.gravity = Gravity.TOP | Gravity.END;
      badgeParams.setMargins(dp(12), dp(12), dp(12), dp(12));
      picArea.addView(badge, badgeParams);
      LinearLayout play = new LinearLayout(this);
      play.setGravity(Gravity.CENTER);
      GradientDrawable playBg = new GradientDrawable();
      playBg.setShape(GradientDrawable.OVAL);
      playBg.setColor(0x55000000);
      play.setBackground(playBg);
      play.addView(icon(IconView.PLAY, TEXT, 22));
      play.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          speakWord(d.displayWord);
        }
      });
      FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
          dp(48), dp(48));
      playParams.gravity = Gravity.BOTTOM | Gravity.END;
      playParams.setMargins(dp(12), dp(12), dp(12), dp(12));
      picArea.addView(play, playParams);
      card.addView(picArea, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT));
    }
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(16), dp(16), dp(16), dp(16));
    card.addView(body, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT));

    LinearLayout wordRow = new LinearLayout(this);
    wordRow.setOrientation(LinearLayout.HORIZONTAL);
    wordRow.setGravity(Gravity.CENTER_VERTICAL);
    wordRow.addView(tv(d.displayWord.toLowerCase(Locale.US), 30, TEXT, true),
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    IconView sp = icon(IconView.SPEAKER, SUB, 24);
    sp.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        speakWord(d.displayWord);
      }
    });
    wordRow.addView(sp);
    IconView dots = icon(IconView.DOTS, SUB, 24);
    LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(
        dp(24), dp(24));
    dotsParams.setMargins(dp(12), 0, 0, 0);
    dots.setLayoutParams(dotsParams);
    dots.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showWordMenu(v, d);
      }
    });
    wordRow.addView(dots);
    body.addView(wordRow);
    body.addView(posDefText(m, 16));
    body.addView(grayFaRowFor(m));
    if (m.example != null && m.example.length() > 0) {
      LinearLayout inset = new LinearLayout(this);
      inset.setOrientation(LinearLayout.VERTICAL);
      inset.setBackground(rounded(CARD2, 12));
      inset.setPadding(dp(14), dp(14), dp(14), dp(14));
      inset.addView(exampleTextView(m.example, d.displayWord));
      body.addView(inset);
      body.addView(grayFaRowForExample(m));
    }
    // v7: no hook section and no All-information list on the card.
    // The whole information lives in Tell Me More (see showDetail).
    // Action row: add, heart, share (icon buttons, no counts), plus
    // Tell Me More with a pink-to-blue gradient border. No swap and
    // no counter hint: all meanings live in Tell Me More.
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
    heart.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean liked = !isLiked(key);
        setLiked(key, liked);
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
        shareText(d.displayWord + ": " + m.definition);
      }
    });
    actions.addView(share);
    LinearLayout tellMore = gradientBorderButton("Tell Me More");
    tellMore.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showDetail(d);
      }
    });
    // v10: compact, about the size of its text (the yellow-marked
    // text area in the user's screenshot), not the old full-width
    // button; a spacer pushes it to the right, as in the reference,
    // with the add, heart, and share icons on the left.
    View tellSpacer = new View(this);
    actions.addView(tellSpacer, new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    LinearLayout.LayoutParams tellParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    tellParams.setMargins(dp(8), 0, 0, 0);
    actions.addView(tellMore, tellParams);
    body.addView(actions);
    // Close sits below the card, full width, as in the reference.
    LinearLayout close = pill(CARD2);
    close.addView(tv("Close", 15, TEXT, false));
    close.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    closeParams.setMargins(0, dp(12), 0, 0);
    close.setLayoutParams(closeParams);
    close.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        onBackPressed();
      }
    });
    cardPopupBox.addView(close);
  }

  /** Tell Me More: the redesigned bottom sheet. Layout matches the
      user's reference sheet (drag handle, centered word plus
      speaker, definition, All definitions and Wikipedia pills,
      gradient divider, section cards); every section shows only
      data the app actually has, and empty sections are omitted
      rather than faked. The sheet is an overlay above the result
      page: tapping the handle or the dimmed backdrop closes it, as
      does Back. */
  private void showDetail(final ResultData d) {
    hideSelectPopup();
    detailData = d;
    detailSeq++;
    if (detailOverlay == null) buildDetailSheet();
    renderDetailContent(d, cachedChatGptSections(d));
    detailOverlay.setVisibility(View.VISIBLE);
    detailOverlay.bringToFront();
    // If a key is saved but no sections are cached yet, fetch them
    // in the background and rebuild the open sheet when they land.
    if (getChatGptKey().length() > 0 && cachedChatGptSections(d) == null) {
      final int mySeq = detailSeq;
      final String word = d.displayWord;
      new Thread(new Runnable() {
        public void run() {
          final String raw = fetchChatGptRaw(word, getChatGptKey());
          final ChatGptSections.Sections s =
              raw == null ? null : ChatGptSections.parse(raw);
          runOnUiThread(new Runnable() {
            public void run() {
              if (mySeq != detailSeq) return;
              if (detailData == null
                  || !word.equals(detailData.displayWord)) return;
              if (detailOverlay == null
                  || detailOverlay.getVisibility() != View.VISIBLE) return;
              if (s != null && ChatGptSections.hasAny(s)) {
                prefs.edit().putString(chatGptCacheKey(word), raw).apply();
              }
              renderDetailContent(detailData,
                  cachedChatGptSections(detailData));
            }
          });
        }
      }).start();
    }
  }

  /** Builds the bottom-sheet overlay shell once: a dimmed backdrop
      (tap closes) plus a sheet with rounded top corners holding a
      scrollable content column. Content is rebuilt per word. */
  /** v10: Show card popup shell: a dimmed backdrop (tap closes)
      plus a popup centered vertically and horizontally, not a
      full-height sheet and not stuck to the bottom. The ScrollView
      caps the popup height (side margins 20dp, top/bottom margins
      24dp) so a tall card scrolls inside the popup instead of
      overflowing. The card itself renders into cardPopupBox. */
  private void buildCardOverlay() {
    cardOverlay = new FrameLayout(this);
    cardOverlay.setVisibility(View.GONE);
    View dim = new View(this);
    dim.setBackgroundColor(0xB3000000);
    dim.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        hideCardPopup();
      }
    });
    cardOverlay.addView(dim, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT));
    ScrollView scroll = new ScrollView(this);
    scroll.setVerticalScrollBarEnabled(false);
    cardPopupBox = new LinearLayout(this);
    cardPopupBox.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(cardPopupBox, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT));
    FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT);
    scrollParams.gravity = Gravity.CENTER;
    scrollParams.setMargins(dp(20), dp(24), dp(20), dp(24));
    cardOverlay.addView(scroll, scrollParams);
    addContentView(cardOverlay, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private void hideCardPopup() {
    if (cardOverlay != null) cardOverlay.setVisibility(View.GONE);
  }

  private void buildDetailSheet() {
    detailOverlay = new FrameLayout(this);
    detailOverlay.setVisibility(View.GONE);
    View dim = new View(this);
    dim.setBackgroundColor(0xB3000000);
    dim.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        hideDetailSheet();
      }
    });
    detailOverlay.addView(dim, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT));
    LinearLayout sheet = new LinearLayout(this);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setBackground(roundedTop(BG, 20));
    sheet.setClickable(true);
    FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT);
    sheetParams.gravity = Gravity.BOTTOM;
    sheetParams.setMargins(0, dp(48), 0, 0);
    ScrollView scroll = new ScrollView(this);
    detailContent = new LinearLayout(this);
    detailContent.setOrientation(LinearLayout.VERTICAL);
    detailContent.setPadding(dp(16), dp(8), dp(16), dp(24));
    scroll.addView(detailContent);
    sheet.addView(scroll, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.MATCH_PARENT));
    detailOverlay.addView(sheet, sheetParams);
    addContentView(detailOverlay, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private void hideDetailSheet() {
    if (detailOverlay != null) detailOverlay.setVisibility(View.GONE);
    detailData = null;
  }


  /** Builds the sheet body for one word. Sections appear only when
      the app actually has their data; empty sections are omitted. */
  private void renderDetailContent(final ResultData d,
      final ChatGptSections.Sections s) {
    detailContent.removeAllViews();
    // Drag handle: centered bar, tap closes the sheet.
    LinearLayout handleRow = new LinearLayout(this);
    handleRow.setOrientation(LinearLayout.HORIZONTAL);
    handleRow.setGravity(Gravity.CENTER);
    handleRow.setPadding(0, dp(4), 0, dp(12));
    View handle = new View(this);
    GradientDrawable handleBg = new GradientDrawable();
    handleBg.setColor(SUB);
    handleBg.setCornerRadius(dp(2));
    handle.setBackground(handleBg);
    handle.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(4)));
    handleRow.addView(handle);
    handleRow.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        hideDetailSheet();
      }
    });
    detailContent.addView(handleRow);
    // Centered word plus speaker pill below it. The pill shows the
    // dictionary phonetic when the app has one, else just the
    // speaker icon; no badge text is ever invented or copied.
    TextView wordView = tv(d.displayWord.toLowerCase(Locale.US), 30, TEXT, true);
    wordView.setGravity(Gravity.CENTER);
    detailContent.addView(wordView);
    LinearLayout speakerRow = new LinearLayout(this);
    speakerRow.setOrientation(LinearLayout.HORIZONTAL);
    speakerRow.setGravity(Gravity.CENTER);
    speakerRow.setPadding(0, dp(8), 0, 0);
    LinearLayout speakerPill = pill(CARD2);
    if (d.phonetic != null && d.phonetic.trim().length() > 0) {
      speakerPill.addView(tv(d.phonetic.trim(), 14, TEXT, false));
      IconView spIcon = icon(IconView.SPEAKER, SUB, 18);
      LinearLayout.LayoutParams spParams = new LinearLayout.LayoutParams(
          dp(18), dp(18));
      spParams.setMargins(dp(6), 0, 0, 0);
      spIcon.setLayoutParams(spParams);
      speakerPill.addView(spIcon);
    } else {
      speakerPill.addView(icon(IconView.SPEAKER, TEXT, 18));
    }
    speakerPill.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        speakWord(d.displayWord);
      }
    });
    speakerRow.addView(speakerPill);
    detailContent.addView(speakerRow);
    // Current definition, centered, with a small FA link below it.
    Meaning cur = currentMeaning(d);
    if (cur != null) {
      TextView def = posDefText(cur, 16);
      def.setGravity(Gravity.CENTER);
      def.setPadding(0, dp(12), 0, 0);
      detailContent.addView(def);
      detailContent.addView(centerFaRowFor(cur));
    }
    // All definitions and Wikipedia pills, centered.
    LinearLayout pillsRow = new LinearLayout(this);
    pillsRow.setOrientation(LinearLayout.HORIZONTAL);
    pillsRow.setGravity(Gravity.CENTER);
    pillsRow.setPadding(0, dp(12), 0, 0);
    final LinearLayout allDefsBox = new LinearLayout(this);
    allDefsBox.setOrientation(LinearLayout.VERTICAL);
    allDefsBox.setVisibility(View.GONE);
    LinearLayout allDefs = pill(CARD2);
    allDefs.addView(tv("All definitions", 14, TEXT, false));
    allDefs.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        if (allDefsBox.getVisibility() == View.VISIBLE) {
          allDefsBox.setVisibility(View.GONE);
          return;
        }
        if (allDefsBox.getChildCount() == 0) {
          for (int i = 0; i < d.meanings.size(); i++) {
            Meaning mm = d.meanings.get(i);
            allDefsBox.addView(posDefText(mm, 16));
            allDefsBox.addView(grayFaRowFor(mm));
          }
        }
        allDefsBox.setVisibility(View.VISIBLE);
      }
    });
    pillsRow.addView(allDefs);
    LinearLayout wiki = pill(CARD2);
    LinearLayout.LayoutParams wikiParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    wikiParams.setMargins(dp(8), 0, 0, 0);
    wiki.setLayoutParams(wikiParams);
    wiki.addView(tv("Wikipedia", 14, TEXT, false));
    wiki.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        openWikipedia(d.displayWord);
      }
    });
    pillsRow.addView(wiki);
    detailContent.addView(pillsRow);
    detailContent.addView(allDefsBox);
    // Pink-to-blue gradient divider.
    View divider = new View(this);
    GradientDrawable divBg = new GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT, new int[]{PINK, BLUE});
    divider.setBackground(divBg);
    LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(2));
    divParams.setMargins(0, dp(16), 0, dp(8));
    detailContent.addView(divider, divParams);
    // Overview: only from real ChatGPT sections, never written here.
    if (s != null && s.overview != null) {
      TextView hidden = hiddenFaView();
      TextView link = grayFaLink();
      wireLazyFa(link, hidden, s.overview);
      detailContent.addView(sectionHeading("Overview", s.overview,
          s.overview, link));
      LinearLayout c = card();
      addParagraphs(c, s.overview, d.displayWord);
      detailContent.addView(c);
      detailContent.addView(hidden);
    }
    // In Action: the app's example sentences, up to 3 plus Show more.
    final List<String> allExamples = collectDetailExamples(d);
    if (!allExamples.isEmpty()) {
      final LinearLayout exCard = card();
      final LinearLayout exRows = new LinearLayout(this);
      exRows.setOrientation(LinearLayout.VERTICAL);
      exCard.addView(exRows);
      final List<String> first = DetailContent.firstExamples(allExamples);
      final List<String> rest = DetailContent.restExamples(allExamples);
      for (int i = 0; i < first.size(); i++) {
        exRows.addView(exampleRow(first.get(i), d.displayWord));
      }
      final LinearLayout exFaBox = new LinearLayout(this);
      exFaBox.setOrientation(LinearLayout.VERTICAL);
      exFaBox.setVisibility(View.GONE);
      final boolean[] exFaPopulated = new boolean[]{false};
      TextView exFaLink = grayFaLink();
      exFaLink.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          if (exFaBox.getVisibility() == View.VISIBLE) {
            exFaBox.setVisibility(View.GONE);
            return;
          }
          if (!exFaPopulated[0]) {
            exFaPopulated[0] = true;
            populateExampleFa(exFaBox, d, allExamples);
          }
          exFaBox.setVisibility(View.VISIBLE);
        }
      });
      detailContent.addView(sectionHeading("In Action",
          joinLines(allExamples), joinLines(allExamples), exFaLink));
      detailContent.addView(exCard);
      if (!rest.isEmpty()) {
        final LinearLayout showMore = pill(CARD2);
        showMore.setGravity(Gravity.CENTER);
        showMore.addView(tv("Show more", 14, TEXT, false));
        showMore.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v) {
            for (int i = 0; i < rest.size(); i++) {
              exRows.addView(exampleRow(rest.get(i), d.displayWord));
            }
            exCard.removeView(showMore);
          }
        });
        exCard.addView(showMore);
      }
      detailContent.addView(exFaBox);
    }
    // How Common: only when a real frequency level exists.
    int freqLevel = -1;
    String freqLabel = null;
    if (s != null) {
      if (DetailContent.validFrequencyLevel(s.frequencyLevel)) {
        freqLevel = s.frequencyLevel;
      } else if (s.frequencyLabel != null) {
        freqLevel = DetailContent.frequencyLevelForLabel(s.frequencyLabel);
      }
      freqLabel = s.frequencyLabel;
      if (freqLabel == null && freqLevel != -1) {
        freqLabel = DetailContent.frequencyLabelForLevel(freqLevel);
      }
    }
    if (freqLevel != -1 && freqLabel != null) {
      detailContent.addView(detailTitle("How Common"));
      LinearLayout c = card();
      c.addView(tv(freqLabel, 15, TEXT, false));
      LinearLayout meter = new LinearLayout(this);
      meter.setOrientation(LinearLayout.HORIZONTAL);
      meter.setPadding(0, dp(12), 0, 0);
      for (int i = 0; i < 7; i++) {
        View sq = new View(this);
        sq.setBackground(rounded(i < freqLevel ? GREEN : CARD2, 8));
        LinearLayout.LayoutParams sqParams = new LinearLayout.LayoutParams(
            0, dp(36), 1f);
        if (i < 6) sqParams.setMargins(0, 0, dp(8), 0);
        meter.addView(sq, sqParams);
      }
      c.addView(meter);
      detailContent.addView(c);
    }
    // Background: only from real ChatGPT sections, never written here.
    if (s != null && s.background != null) {
      TextView hidden = hiddenFaView();
      TextView link = grayFaLink();
      wireLazyFa(link, hidden, s.background);
      detailContent.addView(sectionHeading("Background", s.background,
          s.background, link));
      LinearLayout c = card();
      addParagraphs(c, s.background, d.displayWord);
      detailContent.addView(c);
      detailContent.addView(hidden);
    }
    // Related: the app's synonyms plus similar words, tappable.
    List<String> similarNames = new ArrayList<String>();
    List<WordsData.Word> similarWords = WordIndex.findSimilar(d.displayWord, 8);
    for (int i = 0; i < similarWords.size(); i++) {
      similarNames.add(similarWords.get(i).word);
    }
    final List<String> related =
        DetailContent.relatedWords(d.synonyms, similarNames);
    if (!related.isEmpty()) {
      detailContent.addView(sectionHeading("Related",
          joinLines(related), joinLines(related), null));
      LinearLayout c = card();
      for (int i = 0; i < related.size(); i++) {
        final String rw = related.get(i);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(GREEN);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
            dp(10), dp(10));
        dotParams.setMargins(0, 0, dp(10), 0);
        row.addView(dot, dotParams);
        TextView wv = tv(rw, 16, TEXT, true);
        wv.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v) {
            hideDetailSheet();
            input.setText(rw);
            doLookup(rw);
            navTab("search");
          }
        });
        row.addView(wv);
        c.addView(row);
      }
      detailContent.addView(c);
    }
  }

  /** Section heading row: bold title, optional small gray FA link
      next to it, then play (speaks the section) and "..." (Copy and
      Share for the section). A null FA link means no FA control. */
  private LinearLayout sectionHeading(String title, final String speakText,
      final String shareText, TextView faLink) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(20), 0, dp(8));
    row.addView(tv(title, 18, TEXT, true));
    if (faLink != null) {
      LinearLayout.LayoutParams faParams = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      faParams.setMargins(dp(8), 0, 0, 0);
      row.addView(faLink, faParams);
    }
    row.addView(new View(this), new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    IconView play = icon(IconView.PLAY, SUB, 20);
    play.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        speakWord(speakText);
      }
    });
    row.addView(play);
    IconView dots = icon(IconView.DOTS, SUB, 20);
    LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(
        dp(20), dp(20));
    dotsParams.setMargins(dp(12), 0, 0, 0);
    dots.setLayoutParams(dotsParams);
    dots.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showSectionMenu(v, shareText);
      }
    });
    row.addView(dots);
    return row;
  }

  /** Plain section title with no controls (How Common). */
  private TextView detailTitle(String title) {
    TextView t = tv(title, 18, TEXT, true);
    t.setPadding(0, dp(20), 0, dp(8));
    return t;
  }

  /** Small gray FA text link, as in the references (the v6 result
      page links stay teal; the new card and detail use gray). */
  private TextView grayFaLink() {
    TextView link = tv("FA", 12, SUB, true);
    link.setPadding(dp(8), dp(2), dp(8), dp(2));
    return link;
  }

  /** Hidden Persian line revealed by an FA link. */
  private TextView hiddenFaView() {
    TextView v = selTv("", 15, TEXT, false);
    v.setTextDirection(View.TEXT_DIRECTION_RTL);
    v.setVisibility(View.GONE);
    return v;
  }

  private void wireStoredFa(final TextView link, final View hidden) {
    link.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        hidden.setVisibility(hidden.getVisibility() == View.VISIBLE
            ? View.GONE : View.VISIBLE);
      }
    });
  }

  /** Lazy FA: cached translation first, then the same keyless path,
      never an invented translation. Same handler as lazyFaPillRow. */
  private void wireLazyFa(final TextView link, final TextView hidden,
      final String englishText) {
    link.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        if (hidden.getVisibility() == View.VISIBLE) {
          hidden.setVisibility(View.GONE);
          return;
        }
        String cached = getCachedTranslation(englishText, "auto", "fa");
        if (cached != null) {
          hidden.setText(cached);
          hidden.setVisibility(View.VISIBLE);
          return;
        }
        link.setText("\u2026");
        fetchTranslation(englishText, "auto", "fa", new TranslationCallback() {
          public void onResult(GtxParser.Result r) {
            if (r.status == GtxParser.OK) {
              putCachedTranslation(englishText, "auto", "fa", r.translation);
              hidden.setText(r.translation);
              hidden.setVisibility(View.VISIBLE);
            }
            link.setText("FA");
          }
        });
      }
    });
  }

  /** Gray FA row for one meaning: stored Persian when the app has
      it, lazy translation otherwise. */
  private LinearLayout grayFaRowFor(Meaning m) {
    return grayFaRow(m.definitionFa, m.definition);
  }

  private LinearLayout grayFaRowForExample(Meaning m) {
    return grayFaRow(m.exampleFa, m.example);
  }

  private LinearLayout grayFaRow(String storedFa, String englishText) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setPadding(0, dp(4), 0, dp(4));
    TextView hidden = hiddenFaView();
    if (storedFa != null) hidden.setText(storedFa);
    TextView link = grayFaLink();
    if (storedFa != null && storedFa.length() > 0) {
      wireStoredFa(link, hidden);
    } else {
      wireLazyFa(link, hidden, englishText);
    }
    row.addView(link);
    row.addView(hidden);
    return row;
  }

  /** Centered FA row for the detail header definition. */
  private LinearLayout centerFaRowFor(Meaning m) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setGravity(Gravity.CENTER);
    row.setPadding(0, dp(4), 0, dp(4));
    TextView hidden = hiddenFaView();
    hidden.setGravity(Gravity.CENTER);
    if (m.definitionFa != null) hidden.setText(m.definitionFa);
    TextView link = grayFaLink();
    if (m.definitionFa != null && m.definitionFa.length() > 0) {
      wireStoredFa(link, hidden);
    } else {
      wireLazyFa(link, hidden, m.definition);
    }
    row.addView(link);
    row.addView(hidden);
    return row;
  }

  /** Example text: whole sentence italic, the word itself bold
      italic, as in the reference insets. */
  private SelTextView exampleTextView(String example, String word) {
    SelTextView t = selTv(example, 15, TEXT, false);
    SpannableString s = new SpannableString(example);
    s.setSpan(new StyleSpan(Typeface.ITALIC), 0, example.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    List<DetailContent.Range> ranges =
        DetailContent.boldRanges(example, word);
    for (int i = 0; i < ranges.size(); i++) {
      DetailContent.Range r = ranges.get(i);
      s.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), r.start, r.end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    t.setText(s);
    return t;
  }

  /** Body text with the word bolded (Overview and Background). */
  private SelTextView boldTextView(String text, String word) {
    SelTextView t = selTv(text, 15, TEXT, false);
    SpannableString s = new SpannableString(text);
    List<DetailContent.Range> ranges = DetailContent.boldRanges(text, word);
    for (int i = 0; i < ranges.size(); i++) {
      DetailContent.Range r = ranges.get(i);
      s.setSpan(new StyleSpan(Typeface.BOLD), r.start, r.end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    t.setText(s);
    return t;
  }

  private void addParagraphs(LinearLayout parent, String text, String word) {
    String[] parts = text.split("\n\n");
    for (int i = 0; i < parts.length; i++) {
      String p = parts[i].trim();
      if (p.length() == 0) continue;
      parent.addView(boldTextView(p, word));
    }
  }

  /** One In Action row: green dot plus italic example with the word
      bold italic. */
  private LinearLayout exampleRow(String example, String word) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, dp(6), 0, dp(6));
    View dot = new View(this);
    GradientDrawable dotBg = new GradientDrawable();
    dotBg.setShape(GradientDrawable.OVAL);
    dotBg.setColor(GREEN);
    dot.setBackground(dotBg);
    LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
        dp(10), dp(10));
    dotParams.setMargins(0, dp(6), dp(10), 0);
    row.addView(dot, dotParams);
    row.addView(exampleTextView(example, word),
        new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    return row;
  }

  private List<String> collectDetailExamples(ResultData d) {
    List<String> raw = new ArrayList<String>();
    for (int i = 0; i < d.meanings.size(); i++) {
      raw.add(d.meanings.get(i).example);
    }
    return DetailContent.collectExamples(raw);
  }

  /** Fills the In Action FA box: one Persian line per shown example,
      stored where the app has it, lazy-fetched otherwise. */
  private void populateExampleFa(final LinearLayout box, ResultData d,
      List<String> examples) {
    for (int i = 0; i < examples.size(); i++) {
      final String ex = examples.get(i);
      String stored = null;
      for (int j = 0; j < d.meanings.size(); j++) {
        Meaning mm = d.meanings.get(j);
        if (mm.example != null && mm.example.trim().equals(ex)
            && mm.exampleFa != null && mm.exampleFa.length() > 0) {
          stored = mm.exampleFa;
          break;
        }
      }
      final TextView line = selTv("", 15, TEXT, false);
      line.setTextDirection(View.TEXT_DIRECTION_RTL);
      box.addView(line);
      if (stored != null) {
        line.setText(stored);
      } else {
        String cached = getCachedTranslation(ex, "auto", "fa");
        if (cached != null) {
          line.setText(cached);
        } else {
          line.setText("\u2026");
          fetchTranslation(ex, "auto", "fa", new TranslationCallback() {
            public void onResult(GtxParser.Result r) {
              if (r.status == GtxParser.OK) {
                putCachedTranslation(ex, "auto", "fa", r.translation);
                line.setText(r.translation);
              } else {
                line.setText("");
              }
            }
          });
        }
      }
    }
  }

  private String joinLines(List<String> items) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) sb.append(". ");
      sb.append(items.get(i));
    }
    return sb.toString();
  }

  /** Status badge text from the word's real state. */
  private String cardStatus(ResultData d) {
    if (isLearned(d.savedKey)) return "LEARNED";
    if (isSaved(d.savedKey)) return "LEARNING";
    return "NEW";
  }

  /** Picture bitmap with only the top corners rounded, so the
      picture panel can sit edge to edge in the card. */
  private Bitmap topRoundedBitmap(Bitmap src, int radiusDp) {
    int r = dp(radiusDp);
    Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(),
        Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(out);
    Path path = new Path();
    float[] radii = new float[]{r, r, r, r, 0, 0, 0, 0};
    path.addRoundRect(new android.graphics.RectF(0, 0,
        src.getWidth(), src.getHeight()), radii, Path.Direction.CW);
    canvas.clipPath(path);
    canvas.drawBitmap(src, 0, 0, null);
    return out;
  }

  private GradientDrawable roundedTop(int color, int radiusDp) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    float r = dp(radiusDp);
    d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
    return d;
  }

  /** Button with a pink-to-blue gradient border and dark interior,
      as on the reference Tell Me More button. */
  private LinearLayout gradientBorderButton(String text) {
    LinearLayout outer = new LinearLayout(this);
    outer.setOrientation(LinearLayout.HORIZONTAL);
    outer.setGravity(Gravity.CENTER);
    GradientDrawable border = new GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT, new int[]{PINK, BLUE});
    border.setCornerRadius(dp(12));
    outer.setBackground(border);
    outer.setPadding(dp(1), dp(1), dp(1), dp(1));
    LinearLayout inner = new LinearLayout(this);
    inner.setOrientation(LinearLayout.HORIZONTAL);
    inner.setGravity(Gravity.CENTER);
    inner.setBackground(rounded(CARD, 11));
    inner.setPadding(dp(12), dp(8), dp(12), dp(8));
    inner.addView(tv(text, 14, TEXT, false));
    outer.addView(inner, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT));
    return outer;
  }

  private void showWordMenu(View anchor, final ResultData d) {
    PopupMenu menu = new PopupMenu(this, anchor);
    menu.getMenu().add(0, 1, 0, "Copy");
    menu.getMenu().add(0, 2, 0, "Share");
    menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == 1) {
          copyText(d.displayWord);
        } else {
          Meaning cur = currentMeaning(d);
          shareText(cur == null ? d.displayWord
              : d.displayWord + ": " + cur.definition);
        }
        return true;
      }
    });
    menu.show();
  }

  private void showSectionMenu(View anchor, final String text) {
    PopupMenu menu = new PopupMenu(this, anchor);
    menu.getMenu().add(0, 1, 0, "Copy");
    menu.getMenu().add(0, 2, 0, "Share");
    menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == 1) {
          copyText(text);
        } else {
          shareText(text);
        }
        return true;
      }
    });
    menu.show();
  }

  private void copyText(String text) {
    try {
      ClipboardManager cm =
          (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
      cm.setPrimaryClip(ClipData.newPlainText("Search AI", text));
      Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    } catch (Exception ignored) {
    }
  }

  private void shareText(String text) {
    try {
      Intent send = new Intent(Intent.ACTION_SEND);
      send.setType("text/plain");
      send.putExtra(Intent.EXTRA_TEXT, text);
      startActivity(Intent.createChooser(send, "Share"));
    } catch (Exception ignored) {
    }
  }

  private void openWikipedia(String word) {
    try {
      String page = Uri.encode(word.trim().replace(' ', '_'));
      Intent view = new Intent(Intent.ACTION_VIEW,
          Uri.parse("https://en.wikipedia.org/wiki/" + page));
      startActivity(view);
    } catch (Exception e) {
      Toast.makeText(this, "Could not open Wikipedia.",
          Toast.LENGTH_SHORT).show();
    }
  }

  /** v7 ChatGPT cache key. v6 keys ("chatgpt::") are left in prefs
      unused: v7 re-fetches once per word so the new frequency
      fields are picked up, then caches under the new key. */
  private String chatGptCacheKey(String word) {
    return "chatgpt7::" + word.toLowerCase(Locale.US);
  }

  /** Cached ChatGPT sections for a word, or null when none are
      cached or the cached JSON no longer parses. */
  private ChatGptSections.Sections cachedChatGptSections(ResultData d) {
    String cached = prefs.getString(
        chatGptCacheKey(d.displayWord), null);
    if (cached == null) return null;
    return ChatGptSections.parse(cached);
  }

  // ---- v5: select-to-translate popup. ----

  private void onContentTextSelected(SelTextView view, String text) {
    showSelectPopup(text, view);
  }

  /** v6 popup buttons: equal width (via popupBtnParams), single-line
      labels that can never stack letter by letter, smaller 12sp text
      and 14dp icons with tight padding, so Save and Hear read
      horizontally on one line next to Copy, More, and Close even at
      a narrow phone width. Child order is kept (icon, then text) so
      the Save/Copy label lookups keep working. */
  private LinearLayout popupBtn(String text, int iconKind,
      View.OnClickListener l) {
    LinearLayout p = pill(CARD2);
    p.setGravity(Gravity.CENTER);
    p.setPadding(dp(6), dp(8), dp(6), dp(8));
    if (iconKind >= 0) {
      p.addView(icon(iconKind, TEXT, 14));
      TextView t = tv(text, 12, TEXT, false);
      t.setSingleLine(true);
      t.setEllipsize(android.text.TextUtils.TruncateAt.END);
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      tp.setMargins(dp(4), 0, 0, 0);
      t.setLayoutParams(tp);
      p.addView(t);
    } else {
      TextView t = tv(text, 12, TEXT, false);
      t.setSingleLine(true);
      t.setEllipsize(android.text.TextUtils.TruncateAt.END);
      p.addView(t);
    }
    p.setOnClickListener(l);
    return p;
  }

  private LinearLayout.LayoutParams popupBtnParams(boolean first,
      boolean last) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    p.setMargins(first ? 0 : dp(3), 0, last ? 0 : dp(3), 0);
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
    card.setPadding(dp(12), dp(12), dp(12), dp(12));
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
    // v7: the Tell Me More sheet is also an overlay added after the
    // popup, so the popup must come to the front to stay visible
    // when text inside the sheet is selected.
    popupOverlay.bringToFront();
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
    String cacheKey = chatGptCacheKey(d.displayWord);
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
                  chatGptCacheKey(word), raw).apply();
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
      body.put("max_tokens", 750);
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
          + "sentence answer about the word), frequencyLabel (one of: Very rare, Rare, Uncommon, Fairly common, Common, Very common, Extremely common) and frequencyLevel (integer 1-7 matching the label, 1=Very rare through 7=Extremely common; empty string and 0 when unknown). Never invent etymology or facts "
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

  /** v6 bulk export: resolves one saved word to its file entry,
      exactly like openSavedWord (bundled, verified, cached dict,
      then translation-only). Returns null when nothing is stored.
      A result with no definitions keeps only its translation (if
      any) plus a plain note, never a fake entry. */
  private WordFileBuilder.Entry entryForSavedKey(String key) {
    WordsData.Word bundled = WordIndex.findExact(key);
    if (bundled != null) {
      return entryFromResult(fromWord(bundled),
          sourceForWord(bundled.word));
    }
    VerifiedEntries.Entry v = VerifiedEntries.get(key);
    if (v != null) {
      return entryFromResult(fromVerified(v), sourceForWord(v.word));
    }
    DictParser.DictEntry dict = getCachedDict(key);
    if (dict != null) {
      WordFileBuilder.Entry e = entryFromResult(fromDict(dict,
          getCachedTranslation(dict.word, "auto", "fa")),
          sourceForWord(dict.word));
      if (e.definitions.isEmpty()) {
        e.note = "Full entry not available.";
      }
      return e;
    }
    String trans = prefs.getString("trans::" + key, "");
    if (trans.length() > 0) {
      WordFileBuilder.Entry e = new WordFileBuilder.Entry();
      e.word = key;
      e.persian = trans;
      e.source = "translation";
      e.dateSearched = searchedDateFor(key);
      e.note = "Full entry not available.";
      return e;
    }
    String transFa = prefs.getString("transfa::" + key, "");
    if (transFa.length() > 0) {
      WordFileBuilder.Entry e = new WordFileBuilder.Entry();
      e.word = key;
      e.english = transFa;
      e.source = "translation";
      e.dateSearched = searchedDateFor(key);
      e.note = "Full entry not available.";
      return e;
    }
    return null;
  }

  /** v6 bulk button: saves ALL saved words at once in ONE JSON file
      ("saved-words.json", one entry per word), then opens the share
      sheet. Offline, no storage permission: the file is served by
      the app's own provider with a per-URI read grant. The file
      holds word data only, never a picture. */
  private void shareAllSavedWords() {
    List<WordFileBuilder.Entry> entries =
        new ArrayList<WordFileBuilder.Entry>();
    for (String key : savedSet) {
      WordFileBuilder.Entry e = entryForSavedKey(key);
      if (e != null) entries.add(e);
    }
    if (entries.isEmpty()) {
      Toast.makeText(this, "No saved words to download yet.",
          Toast.LENGTH_SHORT).show();
      return;
    }
    try {
      String filename = "saved-words.json";
      File dir = WordFileProvider.wordFilesDir(this);
      File file = new File(dir, filename);
      FileOutputStream out = new FileOutputStream(file);
      out.write(WordFileBuilder.buildBulkJson(entries).getBytes("UTF-8"));
      out.close();
      Uri uri = new Uri.Builder().scheme("content")
          .authority(WordFileProvider.AUTHORITY)
          .appendPath(filename).build();
      Intent send = new Intent(Intent.ACTION_SEND);
      send.setType("application/json");
      send.putExtra(Intent.EXTRA_STREAM, uri);
      send.putExtra(Intent.EXTRA_SUBJECT, filename);
      send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(send, "Share saved words"));
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
