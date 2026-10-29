package com.sobhan.searchai;

import android.app.Activity;
import android.content.Context;
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

  private SharedPreferences prefs;
  private ScrollView scrollView;
  private LinearLayout searchScreen;
  private LinearLayout savedScreen;
  private LinearLayout historyScreen;
  private LinearLayout settingsScreen;
  private LinearLayout cardScreen;
  private LinearLayout resultBox;
  private LinearLayout recentBox;
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

  private interface TranslationCallback {
    void onResult(GtxParser.Result result);
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
    if (name.equals("search")) refreshRecent();
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

  private void refreshRecent() {
    recentBox.removeAllViews();
    if (historyData.size() == 0) return;
    recentBox.addView(label("RECENT"));
    int count = historyData.size() < 5 ? historyData.size() : 5;
    for (int i = 0; i < count; i++) {
      final String item = historyData.get(i);
      Button b = btn(item, CARD2);
      b.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          input.setText(item);
          doLookup(item);
        }
      });
      recentBox.addView(b);
    }
  }

  private LinearLayout buildSearchScreen() {
    LinearLayout screen = new LinearLayout(this);
    screen.setOrientation(LinearLayout.VERTICAL);
    input = new EditText(this);
    input.setHint("Type a word");
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
    screen.addView(input, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, dp(12), 0, 0);
    Button lookupBtn = btn("Look up", ACCENT);
    lookupBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        doLookup(input.getText().toString());
      }
    });
    row.addView(lookupBtn, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    screen.addView(row);

    recentBox = new LinearLayout(this);
    recentBox.setOrientation(LinearLayout.VERTICAL);
    screen.addView(recentBox);

    resultBox = new LinearLayout(this);
    resultBox.setOrientation(LinearLayout.VERTICAL);
    resultBox.setPadding(0, dp(8), 0, 0);
    screen.addView(resultBox);
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
        refreshRecent();
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
    settingsBox.addView(tv("Version 2", 14, SUB, false));
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
        refreshRecent();
        resultBox.removeAllViews();
        renderWordInto(resultBox, w, false);
        return;
      }
      if (hits.size() > 1) {
        addToHistory(q);
        refreshRecent();
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
        refreshRecent();
        resultBox.removeAllViews();
        renderFaTranslationInto(resultBox, q, cached, false);
        return;
      }
      addToHistory(q);
      refreshRecent();
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
      // English input: exact match, then cache, then similar + network.
      WordsData.Word w = WordIndex.findExact(q);
      if (w != null) {
        addToHistory(w.word);
        refreshRecent();
        resultBox.removeAllViews();
        renderWordInto(resultBox, w, false);
        return;
      }
      String cached = getCachedTranslation(q, "auto", "fa");
      if (cached != null) {
        addToHistory(q);
        refreshRecent();
        resultBox.removeAllViews();
        renderTranslationInto(resultBox, q, cached, false);
        return;
      }
      addToHistory(q);
      refreshRecent();
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
      final TextView waiting = tv("Translating…", 15, SUB, false);
      resultBox.addView(waiting);
      final String fq = q;
      fetchTranslation(q, "auto", "fa", new TranslationCallback() {
        public void onResult(GtxParser.Result r) {
          if (myId != lookupId) return;
          resultBox.removeView(waiting);
          if (r.status == GtxParser.OK) {
            putCachedTranslation(fq, "auto", "fa", r.translation);
            renderTranslationInto(resultBox, fq, r.translation, false);
          } else if (r.status == GtxParser.HTTP_ERROR) {
            resultBox.addView(tv("Translation service unavailable, try again later.", 15, SUB, false));
          } else {
            resultBox.addView(tv("Needs internet for this word.", 15, SUB, false));
          }
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
      conn.setRequestProperty("User-Agent", "SearchAI/2.0");
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

  // ---- Card rendering: Save/Learned sync by tag, never by re-render. ----

  private void renderWordInto(LinearLayout parent, final WordsData.Word w, boolean isCard) {
    LinearLayout card = card();
    card.addView(tv(w.word.toUpperCase(), 22, TEXT, true));
    String posLine = w.pos;
    if (w.phonetic != null && w.phonetic.length() > 0) posLine = posLine + " · " + w.phonetic;
    card.addView(tv(posLine, 14, SUB, false));
    card.addView(label("MEANING"));
    card.addView(tv(w.definition, 16, TEXT, false));
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
    LinearLayout card = card();
    card.addView(tv(query, 20, TEXT, true));
    card.addView(label("PERSIAN MEANING"));
    card.addView(faRow(translation));
    card.addView(tv("Full entry not in offline set.", 14, SUB, false));
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
}
