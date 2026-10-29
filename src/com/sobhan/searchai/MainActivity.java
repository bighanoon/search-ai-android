package com.sobhan.searchai;

import android.app.Activity;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputType;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
  private static final int BG = 0xFF121216;
  private static final int CARD = 0xFF1E1E2A;
  private static final int CARD2 = 0xFF252536;
  private static final int TEXT = 0xFFFFFFFF;
  private static final int SUB = 0xFFB0B0C0;
  private static final int ACCENT = 0xFF9B8CFF;
  private static final int GREEN = 0xFF4CAF7D;
  private static final String[] PROVIDERS = {"Gemini", "ChatGPT", "Perplexity", "Claude", "Grok"};
  private static final int TC_CACHE_CAP = 200;
  private static final int QUERY_CAP = 500;
  // v3 web-home constants: muted heading, recent cap, Try-a-word chips.
  private static final int HEADING = 0xFF9C8E8E;
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
  private LinearLayout recentChipsBox;
  private LinearLayout myWordsBox;
  private LinearLayout tryBox;
  private LinearLayout savedList;
  private TextView savedCount;
  private LinearLayout historyList;
  private LinearLayout settingsBox;
  private LinearLayout cardBox;
  private EditText input;
  private Button navSearch;
  private Button navSaved;
  private Button navHistory;
  private Button navSettings;
  private ArrayDeque<String> backStack = new ArrayDeque<String>();
  private String currentScreen = "search";
  private LinkedHashSet<String> savedSet = new LinkedHashSet<String>();
  private LinkedHashSet<String> learnedSet = new LinkedHashSet<String>();
  private List<String> historyData = new ArrayList<String>();
  private int lookupId = 0;
  // v3: on-device speech for the speaker control (no key, no network call).
  private TextToSpeech tts;
  private String pendingSpeak;

  private interface TranslationCallback {
    void onResult(GtxParser.Result result);
  }

  private interface DictCallback {
    void onResult(DictParser.DictEntry entry);
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

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(16), dp(16), dp(16), dp(8));
    TextView title = tv("Search AI", 22, TEXT, true);
    header.addView(title);
    TextView credit = tv("by SobhaN", 12, SUB, false);
    LinearLayout.LayoutParams creditParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    creditParams.setMargins(dp(12), 0, 0, 0);
    credit.setLayoutParams(creditParams);
    header.addView(credit);
    root.addView(header);

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

  private TextView label(String text) {
    TextView t = tv(text, 12, SUB, true);
    t.setPadding(0, dp(14), 0, dp(4));
    return t;
  }

  private GradientDrawable rounded(int color, int radiusDp) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radiusDp));
    return d;
  }

  private RippleDrawable ripple(int bg) {
    return new RippleDrawable(
        ColorStateList.valueOf(0x33FFFFFF), rounded(bg, 10), null);
  }

  private Button btn(String text, int bg) {
    Button b = new Button(this);
    b.setText(text);
    b.setAllCaps(false);
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    b.setTextColor(TEXT);
    b.setBackground(ripple(bg));
    b.setPadding(dp(14), dp(10), dp(14), dp(10));
    return b;
  }

  private Button navButton(String text, final String screen) {
    Button b = new Button(this);
    b.setText(text);
    b.setAllCaps(false);
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    b.setTextColor(SUB);
    b.setBackgroundColor(CARD);
    b.setPadding(dp(4), dp(10), dp(4), dp(10));
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

  private LinearLayout faRow(String faText) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setGravity(Gravity.CENTER_HORIZONTAL);
    row.setPadding(0, dp(6), 0, dp(6));
    final TextView faView = new TextView(this);
    faView.setText(faText);
    faView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    faView.setTextColor(TEXT);
    faView.setTextDirection(View.TEXT_DIRECTION_RTL);
    faView.setVisibility(View.GONE);
    Button faBtn = btn("FA", CARD2);
    faBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    faBtn.setPadding(dp(18), dp(6), dp(18), dp(6));
    faBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        if (faView.getVisibility() == View.VISIBLE) {
          faView.setVisibility(View.GONE);
        } else {
          faView.setVisibility(View.VISIBLE);
        }
      }
    });
    row.addView(faBtn);
    row.addView(faView);
    return row;
  }

  // ---- Navigation: a back stack replaces the old cardSource/last* fields.
  // Back never exits from a non-search screen; it returns to the previous
  // screen, and the result view is never re-rendered on Back, so revealed
  // FA text stays revealed. ----

  private void showScreen(String name) {
    currentScreen = name;
    searchScreen.setVisibility(name.equals("search") ? View.VISIBLE : View.GONE);
    savedScreen.setVisibility(name.equals("saved") ? View.VISIBLE : View.GONE);
    historyScreen.setVisibility(name.equals("history") ? View.VISIBLE : View.GONE);
    settingsScreen.setVisibility(name.equals("settings") ? View.VISIBLE : View.GONE);
    cardScreen.setVisibility(name.equals("card") ? View.VISIBLE : View.GONE);
    navSearch.setTextColor(name.equals("search") ? ACCENT : SUB);
    navSaved.setTextColor(name.equals("saved") ? ACCENT : SUB);
    navHistory.setTextColor(name.equals("history") ? ACCENT : SUB);
    navSettings.setTextColor(name.equals("settings") ? ACCENT : SUB);
    if (scrollView != null) scrollView.scrollTo(0, 0);
    if (name.equals("saved")) refreshSaved();
    if (name.equals("history")) refreshHistory();
    if (name.equals("settings")) refreshSettings();
    if (name.equals("search")) refreshHome();
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

  private Button backButton() {
    Button back = btn("Back", CARD2);
    back.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        onBackPressed();
      }
    });
    return back;
  }

  private void showCard(WordsData.Word w) {
    cardBox.removeAllViews();
    cardBox.addView(backButton());
    renderWordInto(cardBox, w, true);
    navigate("card");
  }

  private void showTranslationCard(String query, String translation) {
    cardBox.removeAllViews();
    cardBox.addView(backButton());
    renderTranslationInto(cardBox, query, translation, true);
    navigate("card");
  }

  private void showFaTranslationCard(String query, String translation) {
    cardBox.removeAllViews();
    cardBox.addView(backButton());
    renderFaTranslationInto(cardBox, query, translation, true);
    navigate("card");
  }

  private void showDictCard(DictParser.DictEntry e) {
    cardBox.removeAllViews();
    cardBox.addView(backButton());
    renderDictInto(cardBox, e, true);
    navigate("card");
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

  // ---- Save/Learned sync by view tag: no re-render, FA state preserved. ----

  private Button findButtonByTag(ViewGroup root, String tag) {
    for (int i = 0; i < root.getChildCount(); i++) {
      View v = root.getChildAt(i);
      if (tag.equals(v.getTag()) && v instanceof Button) return (Button) v;
      if (v instanceof ViewGroup) {
        Button found = findButtonByTag((ViewGroup) v, tag);
        if (found != null) return found;
      }
    }
    return null;
  }

  private void syncOneButton(ViewGroup root, String tag, boolean on,
      String onText, String offText, int onBg, int offBg) {
    Button b = findButtonByTag(root, tag);
    if (b == null) return;
    b.setText(on ? onText : offText);
    b.setBackground(ripple(on ? onBg : offBg));
  }

  private void syncButtons(String key) {
    syncOneButton(resultBox, "save:" + key, isSaved(key),
        "Saved", "Save", GREEN, ACCENT);
    syncOneButton(cardBox, "save:" + key, isSaved(key),
        "Saved", "Save", GREEN, ACCENT);
    syncOneButton(resultBox, "learn:" + key, isLearned(key),
        "Learned", "Mark learned", GREEN, CARD2);
    syncOneButton(cardBox, "learn:" + key, isLearned(key),
        "Learned", "Mark learned", GREEN, CARD2);
  }

  // ---- Screens ----

  // ---- Web home (v3): recent chips with remove, My Words cards,
  // Try-a-word chips — all from this app's own store, never the web's. ----

  private void refreshHome() {
    recentChipsBox.removeAllViews();
    if (historyData.size() > 0) {
      recentChipsBox.addView(label("Recent searches"));
      int count = historyData.size() < RECENT_HOME_CAP
          ? historyData.size() : RECENT_HOME_CAP;
      for (int i = 0; i < count; i++) {
        final String item = historyData.get(i);
        recentChipsBox.addView(chipRow(item, new View.OnClickListener() {
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
    }
    myWordsBox.removeAllViews();
    myWordsBox.addView(label("My Words"));
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
    tryBox.removeAllViews();
    tryBox.addView(label("Try a word"));
    for (int i = 0; i < TRY_WORDS.length; i++) {
      final String item = TRY_WORDS[i];
      Button b = btn(item, CARD2);
      b.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          input.setText(item);
          doLookup(item);
        }
      });
      tryBox.addView(b);
    }
  }

  private LinearLayout chipRow(String item, View.OnClickListener onTap,
      View.OnClickListener onRemove) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    Button chip = btn(item, CARD2);
    chip.setOnClickListener(onTap);
    row.addView(chip, new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    Button x = btn("×", CARD2);
    x.setOnClickListener(onRemove);
    LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    xp.setMargins(dp(8), 0, 0, 0);
    row.addView(x, xp);
    return row;
  }

  /** This app's own Persian for a word, for the My Words card line. */
  private String persianFor(String item) {
    WordsData.Word w = WordIndex.findExact(item);
    if (w != null && w.definitionFa != null && w.definitionFa.length() > 0) {
      return w.definitionFa;
    }
    DictParser.DictEntry e = getCachedDict(item);
    if (e != null) {
      String t = getCachedTranslation(e.word, "auto", "fa");
      if (t != null) return t;
    }
    String t = prefs.getString("trans::" + item, null);
    if (t != null) return t;
    return prefs.getString("transfa::" + item, null);
  }

  private LinearLayout myWordCard(final String item) {
    LinearLayout c = card();
    c.setBackground(ripple(CARD));
    c.addView(tv(item, 17, TEXT, true));
    String fa = persianFor(item);
    if (fa != null) {
      TextView faView = tv(fa, 15, SUB, false);
      faView.setTextDirection(View.TEXT_DIRECTION_RTL);
      c.addView(faView);
    }
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, dp(8), 0, 0);
    TextView p = tv(isLearned(item) ? "Learned" : "Saved", 13, TEXT, true);
    p.setBackground(rounded(isLearned(item) ? GREEN : ACCENT, 12));
    p.setPadding(dp(12), dp(4), dp(12), dp(4));
    row.addView(p);
    // CEFR badge only where verified; otherwise no badge at all.
    String level = WordIndex.cefrFor(item);
    if (level != null) {
      TextView lv = tv(level, 13, TEXT, true);
      lv.setBackground(rounded(CARD2, 12));
      lv.setPadding(dp(12), dp(4), dp(12), dp(4));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      lp.setMargins(dp(8), 0, 0, 0);
      lv.setLayoutParams(lp);
      row.addView(lv);
    }
    c.addView(row);
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
    // Web-home heading, muted and large.
    screen.addView(tv("Type any word...", 28, HEADING, true));
    input = new EditText(this);
    input.setHint("Type a word...");
    input.setHintTextColor(SUB);
    input.setTextColor(TEXT);
    input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    input.setSingleLine(true);
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
    input.setBackground(rounded(CARD, 10));
    input.setPadding(dp(14), dp(12), dp(14), dp(12));
    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    ip.setMargins(0, dp(16), 0, 0);
    screen.addView(input, ip);

    // Look up row: pill plus direction indicator, FA label, speaker.
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(12), 0, 0);
    Button lookupBtn = btn("Look up", ACCENT);
    lookupBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        doLookup(input.getText().toString());
      }
    });
    row.addView(lookupBtn, new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    TextView dir = tv("English ⇄ Persian", 14, SUB, false);
    LinearLayout.LayoutParams dirParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    dirParams.setMargins(dp(12), 0, 0, 0);
    row.addView(dir, dirParams);
    TextView fa = tv("FA", 13, TEXT, true);
    fa.setBackground(rounded(CARD2, 12));
    fa.setPadding(dp(12), dp(4), dp(12), dp(4));
    LinearLayout.LayoutParams faParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    faParams.setMargins(dp(8), 0, 0, 0);
    row.addView(fa, faParams);
    Button hear = btn("🔊", CARD2);
    hear.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        speakWord(input.getText().toString().trim());
      }
    });
    LinearLayout.LayoutParams hearParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    hearParams.setMargins(dp(8), 0, 0, 0);
    row.addView(hear, hearParams);
    screen.addView(row);

    // Select-text tip line, as on the web home.
    TextView tip = tv(
        "Tip: select any text on this page — press and hold a word and its translation appears instantly.",
        13, SUB, false);
    LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    tipParams.setMargins(0, dp(12), 0, 0);
    screen.addView(tip, tipParams);

    // History and Settings pills, same actions as the bottom nav.
    LinearLayout pills = new LinearLayout(this);
    pills.setOrientation(LinearLayout.HORIZONTAL);
    pills.setPadding(0, dp(12), 0, 0);
    Button histBtn = btn("History", CARD2);
    histBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        navTab("history");
      }
    });
    pills.addView(histBtn, new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    Button setBtn = btn("Settings", CARD2);
    setBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        navTab("settings");
      }
    });
    LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    setParams.setMargins(dp(12), 0, 0, 0);
    pills.addView(setBtn, setParams);
    screen.addView(pills);

    resultBox = new LinearLayout(this);
    resultBox.setOrientation(LinearLayout.VERTICAL);
    resultBox.setPadding(0, dp(8), 0, 0);
    screen.addView(resultBox);
    recentChipsBox = new LinearLayout(this);
    recentChipsBox.setOrientation(LinearLayout.VERTICAL);
    screen.addView(recentChipsBox);
    myWordsBox = new LinearLayout(this);
    myWordsBox.setOrientation(LinearLayout.VERTICAL);
    screen.addView(myWordsBox);
    tryBox = new LinearLayout(this);
    tryBox.setOrientation(LinearLayout.VERTICAL);
    screen.addView(tryBox);
    return screen;
  }

  private LinearLayout buildSavedScreen() {
    LinearLayout screen = new LinearLayout(this);
    screen.setOrientation(LinearLayout.VERTICAL);
    screen.addView(label("SAVED WORDS"));
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
    screen.addView(label("HISTORY"));
    historyList = new LinearLayout(this);
    historyList.setOrientation(LinearLayout.VERTICAL);
    screen.addView(historyList);
    Button clearBtn = btn("Clear history", CARD2);
    clearBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        historyData.clear();
        persistHistory();
        refreshHistory();
        refreshHome();
      }
    });
    screen.addView(clearBtn);
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
      if (isLearned(item)) labelText = labelText + " · Learned";
      Button b = btn(labelText, CARD2);
      b.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          openSavedWord(savedItem);
        }
      });
      savedList.addView(b);
    }
  }

  private void openSavedWord(String item) {
    WordsData.Word w = WordIndex.findExact(item);
    if (w != null) {
      showCard(w);
      return;
    }
    DictParser.DictEntry e = getCachedDict(item);
    if (e != null) {
      showDictCard(e);
      return;
    }
    String trans = prefs.getString("trans::" + item, "");
    if (trans.length() > 0) {
      showTranslationCard(item, trans);
      return;
    }
    String transFa = prefs.getString("transfa::" + item, "");
    if (transFa.length() > 0) {
      showFaTranslationCard(item, transFa);
      return;
    }
    // Nothing stored for this item: show it in search without
    // re-running a lookup, so history is not polluted by the fallback.
    input.setText(item);
    navTab("search");
  }

  private void refreshHistory() {
    historyList.removeAllViews();
    if (historyData.size() == 0) {
      historyList.addView(tv("No history yet.", 15, SUB, false));
      return;
    }
    for (int i = 0; i < historyData.size(); i++) {
      final String item = historyData.get(i);
      Button b = btn(item, CARD2);
      b.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          input.setText(item);
          doLookup(item);
          navTab("search");
        }
      });
      historyList.addView(b);
    }
  }

  private void refreshSettings() {
    settingsBox.removeAllViews();
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
      Button useBtn = btn(isSelected ? "Selected for answers" : "Use for answers",
          isSelected ? GREEN : CARD2);
      // A provider whose switch is off cannot be picked for answers.
      useBtn.setEnabled(isOn);
      if (!isOn) useBtn.setAlpha(0.5f);
      useBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          if (!prefs.getBoolean("on_" + name, false)) return;
          prefs.edit().putString("provider", name).apply();
          refreshSettings();
        }
      });
      card.addView(useBtn);
      settingsBox.addView(card);
    }
    settingsBox.addView(tv("Search AI by SobhaN", 14, SUB, false));
    settingsBox.addView(tv("Version 3", 14, SUB, false));
    settingsBox.addView(tv("Saved words and history stay on this phone.", 14, SUB, false));
  }

  // ---- Lookup: direction comes from the query script, never a toggle. ----

  private void doLookup(String raw) {
    String q = raw == null ? "" : raw.replace("\n", " ").trim();
    if (q.length() == 0) {
      resultBox.removeAllViews();
      resultBox.addView(tv("Type a word first.", 15, SUB, false));
      return;
    }
    lookupId++;
    final int myId = lookupId;
    if (WordIndex.isPersian(q)) {
      // Persian input: bundled reverse index first, then cache, then net.
      List<WordsData.Word> hits = WordIndex.findByFa(q, 8);
      if (hits.size() == 1) {
        WordsData.Word w = hits.get(0);
        addToHistory(w.word);
        refreshHome();
        resultBox.removeAllViews();
        renderWordInto(resultBox, w, false);
        return;
      }
      if (hits.size() > 1) {
        addToHistory(q);
        refreshHome();
        resultBox.removeAllViews();
        resultBox.addView(label("DID YOU MEAN?"));
        for (int i = 0; i < hits.size(); i++) {
          final WordsData.Word hw = hits.get(i);
          Button b = btn(hw.word, CARD2);
          b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
              input.setText(hw.word);
              doLookup(hw.word);
            }
          });
          resultBox.addView(b);
        }
        return;
      }
      String cached = getCachedTranslation(q, "fa", "en");
      if (cached != null) {
        addToHistory(q);
        refreshHome();
        resultBox.removeAllViews();
        renderFaTranslationInto(resultBox, q, cached, false);
        return;
      }
      addToHistory(q);
      refreshHome();
      resultBox.removeAllViews();
      final TextView waiting = tv("Translating…", 15, SUB, false);
      resultBox.addView(waiting);
      final String fq = q;
      fetchTranslation(q, "fa", "en", new TranslationCallback() {
        public void onResult(GtxParser.Result r) {
          if (myId != lookupId) return;
          resultBox.removeView(waiting);
          if (r.status == GtxParser.OK) {
            putCachedTranslation(fq, "fa", "en", r.translation);
            renderFaTranslationInto(resultBox, fq, r.translation, false);
          } else if (r.status == GtxParser.HTTP_ERROR) {
            resultBox.addView(tv("Translation service unavailable, try again later.", 15, SUB, false));
          } else {
            resultBox.addView(tv("Needs internet for this word.", 15, SUB, false));
          }
        }
      });
    } else {
      // English input: exact match, then saved full entry, then cached
      // translation, then similar words plus network (full entry first).
      WordsData.Word w = WordIndex.findExact(q);
      if (w != null) {
        addToHistory(w.word);
        refreshHome();
        resultBox.removeAllViews();
        renderWordInto(resultBox, w, false);
        return;
      }
      DictParser.DictEntry cachedDict = getCachedDict(q);
      if (cachedDict != null) {
        addToHistory(cachedDict.word);
        refreshHome();
        resultBox.removeAllViews();
        renderDictInto(resultBox, cachedDict, false);
        return;
      }
      String cached = getCachedTranslation(q, "auto", "fa");
      if (cached != null) {
        addToHistory(q);
        refreshHome();
        resultBox.removeAllViews();
        renderTranslationInto(resultBox, q, cached, false);
        return;
      }
      addToHistory(q);
      refreshHome();
      resultBox.removeAllViews();
      resultBox.addView(tv("No offline entry for \"" + q + "\".", 15, SUB, false));
      List<WordsData.Word> sims = WordIndex.findSimilar(q, 8);
      if (sims.size() > 0) {
        resultBox.addView(label("SIMILAR WORDS"));
        for (int i = 0; i < sims.size(); i++) {
          final WordsData.Word sw = sims.get(i);
          Button b = btn(sw.word, CARD2);
          b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
              input.setText(sw.word);
              doLookup(sw.word);
            }
          });
          resultBox.addView(b);
        }
      }
      final TextView waiting = tv("Loading full entry…", 15, SUB, false);
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
            renderDictInto(resultBox, entry, false);
          } else if (storedTrans[0] != null) {
            resultBox.removeView(waiting);
            renderTranslationResult(storedTrans[0], fq);
          } else {
            waiting.setText("Translating…");
          }
        }
      });
      fetchTranslation(q, "auto", "fa", new TranslationCallback() {
        public void onResult(GtxParser.Result r) {
          if (myId != lookupId) return;
          if (dictWon[0]) {
            if (r.status == GtxParser.OK && wonEntry[0] != null) {
              putCachedTranslation(wonEntry[0].word, "auto", "fa", r.translation);
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
      conn.setRequestProperty("User-Agent", "SearchAI/3.0");
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

  // ---- Dictionary full entries (v3): keyless source, cached locally
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
      conn.setRequestProperty("User-Agent", "SearchAI/3.0");
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

  private void renderTranslationResult(GtxParser.Result r, String fq) {
    if (r.status == GtxParser.OK) {
      putCachedTranslation(fq, "auto", "fa", r.translation);
      renderTranslationInto(resultBox, fq, r.translation, false,
          "Full entry not available.");
    } else if (r.status == GtxParser.HTTP_ERROR) {
      resultBox.addView(tv("Translation service unavailable, try again later.", 15, SUB, false));
    } else {
      resultBox.addView(tv("Needs internet for this word.", 15, SUB, false));
    }
  }

  // ---- Card rendering: Save/Learned sync by tag, never by re-render. ----

  private void renderWordInto(LinearLayout parent, final WordsData.Word w, boolean isCard) {
    LinearLayout card = card();
    card.addView(tv(w.word.toUpperCase(), 22, TEXT, true));
    String posLine = w.pos;
    if (w.phonetic != null && w.phonetic.length() > 0) posLine = posLine + " · " + w.phonetic;
    card.addView(tv(posLine, 14, SUB, false));
    card.addView(label("MEANING"));
    TextView def = tv(w.definition, 16, TEXT, false);
    def.setTextIsSelectable(true);
    card.addView(def);
    if (w.definitionFa != null && w.definitionFa.length() > 0) {
      card.addView(faRow(w.definitionFa));
    }
    card.addView(label("EXAMPLE"));
    LinearLayout inset = new LinearLayout(this);
    inset.setOrientation(LinearLayout.VERTICAL);
    inset.setBackground(rounded(CARD2, 12));
    inset.setPadding(dp(12), dp(12), dp(12), dp(12));
    TextView ex = tv(w.example, 15, TEXT, false);
    ex.setTypeface(null, Typeface.ITALIC);
    ex.setTextIsSelectable(true);
    inset.addView(ex);
    card.addView(inset);
    if (w.exampleFa != null && w.exampleFa.length() > 0) {
      card.addView(faRow(w.exampleFa));
    }
    if (w.synonyms != null && w.synonyms.length() > 0) {
      card.addView(tv("Close words: " + w.synonyms.replace(", ", " · "), 14, SUB, false));
    }
    if (w.antonyms != null && w.antonyms.length() > 0) {
      card.addView(tv("Opposite words: " + w.antonyms.replace(", ", " · "), 14, SUB, false));
    }
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, dp(12), 0, 0);
    final Button saveBtn = btn(isSaved(w.word) ? "Saved" : "Save", isSaved(w.word) ? GREEN : ACCENT);
    saveBtn.setTag("save:" + w.word);
    saveBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowSaved = !isSaved(w.word);
        setSaved(w.word, nowSaved);
        syncButtons(w.word);
      }
    });
    row.addView(saveBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    if (!isCard) {
      View spacer = new View(this);
      spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), LinearLayout.LayoutParams.WRAP_CONTENT));
      row.addView(spacer);
      Button showBtn = btn("Show card", CARD2);
      showBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          showCard(w);
        }
      });
      row.addView(showBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }
    card.addView(row);
    final Button learnBtn = btn(isLearned(w.word) ? "Learned" : "Mark learned",
        isLearned(w.word) ? GREEN : CARD2);
    learnBtn.setTag("learn:" + w.word);
    learnBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowLearned = !isLearned(w.word);
        setLearned(w.word, nowLearned);
        syncButtons(w.word);
      }
    });
    LinearLayout.LayoutParams learnParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    learnParams.setMargins(0, dp(8), 0, 0);
    card.addView(learnBtn, learnParams);
    parent.addView(card);
  }

  private void renderTranslationInto(LinearLayout parent, final String query,
      final String translation, boolean isCard) {
    renderTranslationInto(parent, query, translation, isCard,
        "Full entry not in offline set.");
  }

  private void renderTranslationInto(LinearLayout parent, final String query,
      final String translation, boolean isCard, String note) {
    LinearLayout card = card();
    card.addView(tv(query, 20, TEXT, true));
    card.addView(label("PERSIAN MEANING"));
    card.addView(faRow(translation));
    card.addView(tv(note, 14, SUB, false));
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, dp(12), 0, 0);
    final Button saveBtn = btn(isSaved(query) ? "Saved" : "Save", isSaved(query) ? GREEN : ACCENT);
    saveBtn.setTag("save:" + query);
    saveBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowSaved = !isSaved(query);
        setSaved(query, nowSaved);
        if (nowSaved) {
          prefs.edit().putString("trans::" + query, translation).apply();
        } else {
          prefs.edit().remove("trans::" + query).apply();
        }
        syncButtons(query);
      }
    });
    row.addView(saveBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    if (!isCard) {
      View spacer = new View(this);
      spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), LinearLayout.LayoutParams.WRAP_CONTENT));
      row.addView(spacer);
      Button showBtn = btn("Show card", CARD2);
      showBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          showTranslationCard(query, translation);
        }
      });
      row.addView(showBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }
    card.addView(row);
    parent.addView(card);
  }

  private String joinWords(List<String> words) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.size(); i++) {
      if (i > 0) sb.append(" · ");
      sb.append(words.get(i));
    }
    return sb.toString();
  }

  // FA tap-to-reveal for fetched text: cached translation first, then
  // the same keyless translation path, never an invented translation.
  private LinearLayout lazyFaRow(final String englishText) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setGravity(Gravity.CENTER_HORIZONTAL);
    row.setPadding(0, dp(6), 0, dp(6));
    final TextView faView = new TextView(this);
    faView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    faView.setTextColor(TEXT);
    faView.setTextDirection(View.TEXT_DIRECTION_RTL);
    faView.setVisibility(View.GONE);
    final Button faBtn = btn("FA", CARD2);
    faBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    faBtn.setPadding(dp(18), dp(6), dp(18), dp(6));
    faBtn.setOnClickListener(new View.OnClickListener() {
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
        faBtn.setText("…");
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
    row.addView(faBtn);
    row.addView(faView);
    return row;
  }

  // Same full card shape as a bundled word: definitions, examples,
  // synonyms, opposites, FA tap-to-reveal throughout, instant Save.
  // The entry itself is already cached, so Save files the full entry.
  private void renderDictInto(LinearLayout parent, final DictParser.DictEntry e,
      boolean isCard) {
    LinearLayout card = card();
    card.addView(tv(e.word, 22, TEXT, true));
    String posLine = e.pos;
    if (e.phonetic != null && e.phonetic.length() > 0) {
      posLine = posLine.length() > 0 ? posLine + " · " + e.phonetic : e.phonetic;
    }
    if (posLine.length() > 0) card.addView(tv(posLine, 14, SUB, false));
    for (int i = 0; i < e.defs.size(); i++) {
      DictParser.Def d = e.defs.get(i);
      card.addView(label(i == 0 ? "MEANING" : "MEANING " + (i + 1)));
      TextView def = tv(d.definition, 16, TEXT, false);
      def.setTextIsSelectable(true);
      card.addView(def);
      card.addView(lazyFaRow(d.definition));
      if (d.example != null && d.example.length() > 0) {
        card.addView(label("EXAMPLE"));
        LinearLayout inset = new LinearLayout(this);
        inset.setOrientation(LinearLayout.VERTICAL);
        inset.setBackground(rounded(CARD2, 12));
        inset.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView ex = tv(d.example, 15, TEXT, false);
        ex.setTypeface(null, Typeface.ITALIC);
        ex.setTextIsSelectable(true);
        inset.addView(ex);
        card.addView(inset);
        card.addView(lazyFaRow(d.example));
      }
    }
    if (e.synonyms.size() > 0) {
      card.addView(tv("Close words: " + joinWords(e.synonyms), 14, SUB, false));
    }
    if (e.antonyms.size() > 0) {
      card.addView(tv("Opposite words: " + joinWords(e.antonyms), 14, SUB, false));
    }
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, dp(12), 0, 0);
    final Button saveBtn = btn(isSaved(e.word) ? "Saved" : "Save",
        isSaved(e.word) ? GREEN : ACCENT);
    saveBtn.setTag("save:" + e.word);
    saveBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowSaved = !isSaved(e.word);
        setSaved(e.word, nowSaved);
        syncButtons(e.word);
      }
    });
    row.addView(saveBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    if (!isCard) {
      View spacer = new View(this);
      spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), LinearLayout.LayoutParams.WRAP_CONTENT));
      row.addView(spacer);
      Button showBtn = btn("Show card", CARD2);
      showBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          showDictCard(e);
        }
      });
      row.addView(showBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }
    card.addView(row);
    final Button learnBtn = btn(isLearned(e.word) ? "Learned" : "Mark learned",
        isLearned(e.word) ? GREEN : CARD2);
    learnBtn.setTag("learn:" + e.word);
    learnBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowLearned = !isLearned(e.word);
        setLearned(e.word, nowLearned);
        syncButtons(e.word);
      }
    });
    LinearLayout.LayoutParams learnParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    learnParams.setMargins(0, dp(8), 0, 0);
    card.addView(learnBtn, learnParams);
    parent.addView(card);
  }

  private void renderFaTranslationInto(LinearLayout parent, final String query,
      final String translation, boolean isCard) {
    LinearLayout card = card();
    card.addView(tv(query, 20, TEXT, true));
    card.addView(label("ENGLISH"));
    card.addView(tv(translation, 16, TEXT, false));
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, dp(12), 0, 0);
    final Button saveBtn = btn(isSaved(query) ? "Saved" : "Save", isSaved(query) ? GREEN : ACCENT);
    saveBtn.setTag("save:" + query);
    saveBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowSaved = !isSaved(query);
        setSaved(query, nowSaved);
        if (nowSaved) {
          prefs.edit().putString("transfa::" + query, translation).apply();
        } else {
          prefs.edit().remove("transfa::" + query).apply();
        }
        syncButtons(query);
      }
    });
    row.addView(saveBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    if (!isCard) {
      View spacer = new View(this);
      spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), LinearLayout.LayoutParams.WRAP_CONTENT));
      row.addView(spacer);
      Button showBtn = btn("Show card", CARD2);
      showBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          showFaTranslationCard(query, translation);
        }
      });
      row.addView(showBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }
    card.addView(row);
    parent.addView(card);
  }

  // Speaker control: on-device speech, no key, no network call.
  // Persian input is spoken with a Persian voice, English otherwise.
  private void speakWord(String text) {
    if (text == null || text.length() == 0) return;
    if (tts == null) {
      pendingSpeak = text;
      tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
        public void onInit(int status) {
          if (status == TextToSpeech.SUCCESS && pendingSpeak != null) {
            speakNow(pendingSpeak);
            pendingSpeak = null;
          }
        }
      });
      return;
    }
    speakNow(text);
  }

  private void speakNow(String text) {
    Locale loc = WordIndex.isPersian(text)
        ? new Locale("fa", "IR") : Locale.US;
    tts.setLanguage(loc);
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "searchai-speak");
  }

  @Override
  protected void onDestroy() {
    if (tts != null) {
      tts.shutdown();
      tts = null;
    }
    super.onDestroy();
  }
}
