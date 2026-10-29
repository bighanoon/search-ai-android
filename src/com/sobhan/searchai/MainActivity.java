package com.sobhan.searchai;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
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
import java.util.ArrayList;
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

  private SharedPreferences prefs;
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
  private Button dirButton;
  private boolean faDirection = false;
  private WordsData.Word lastWord = null;
  private String lastQuery = null;
  private String lastTranslation = null;
  private String lastKind = "";
  private String cardSource = "search";
  private int lookupId = 0;

  private interface TranslationCallback {
    void onResult(String translation);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prefs = getSharedPreferences("searchai", MODE_PRIVATE);
    faDirection = prefs.getBoolean("dir", false);

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

  private Button btn(String text, int bg) {
    Button b = new Button(this);
    b.setText(text);
    b.setAllCaps(false);
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    b.setTextColor(TEXT);
    b.setBackground(rounded(bg, 10));
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
        showScreen(screen);
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

  private void showScreen(String name) {
    searchScreen.setVisibility(name.equals("search") ? View.VISIBLE : View.GONE);
    savedScreen.setVisibility(name.equals("saved") ? View.VISIBLE : View.GONE);
    historyScreen.setVisibility(name.equals("history") ? View.VISIBLE : View.GONE);
    settingsScreen.setVisibility(name.equals("settings") ? View.VISIBLE : View.GONE);
    cardScreen.setVisibility(name.equals("card") ? View.VISIBLE : View.GONE);
    navSearch.setTextColor(name.equals("search") ? ACCENT : SUB);
    navSaved.setTextColor(name.equals("saved") ? ACCENT : SUB);
    navHistory.setTextColor(name.equals("history") ? ACCENT : SUB);
    navSettings.setTextColor(name.equals("settings") ? ACCENT : SUB);
    if (name.equals("saved")) refreshSaved();
    if (name.equals("history")) refreshHistory();
    if (name.equals("settings")) refreshSettings();
    if (name.equals("search")) refreshRecent();
  }

  private List<String> getList(String key) {
    List<String> out = new ArrayList<String>();
    String raw = prefs.getString(key, "");
    if (raw.length() == 0) return out;
    String[] parts = raw.split("\n");
    for (int i = 0; i < parts.length; i++) {
      if (parts[i].length() > 0) out.add(parts[i]);
    }
    return out;
  }

  private void setList(String key, List<String> list) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) sb.append("\n");
      sb.append(list.get(i));
    }
    prefs.edit().putString(key, sb.toString()).apply();
  }

  private boolean listContains(List<String> list, String v) {
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i).equals(v)) return true;
    }
    return false;
  }

  private void addToHistory(String q) {
    List<String> h = getList("history");
    List<String> next = new ArrayList<String>();
    next.add(q);
    for (int i = 0; i < h.size(); i++) {
      if (!h.get(i).equals(q)) next.add(h.get(i));
    }
    while (next.size() > 50) next.remove(next.size() - 1);
    setList("history", next);
  }

  private boolean isSaved(String w) {
    return listContains(getList("saved"), w);
  }

  private void setSaved(String w, boolean saved) {
    List<String> s = getList("saved");
    if (saved) {
      if (!listContains(s, w)) s.add(w);
    } else {
      List<String> next = new ArrayList<String>();
      for (int i = 0; i < s.size(); i++) {
        if (!s.get(i).equals(w)) next.add(s.get(i));
      }
      s = next;
    }
    setList("saved", s);
  }

  private boolean isLearned(String w) {
    return listContains(getList("learned"), w);
  }

  private void setLearned(String w, boolean learned) {
    List<String> s = getList("learned");
    if (learned) {
      if (!listContains(s, w)) s.add(w);
    } else {
      List<String> next = new ArrayList<String>();
      for (int i = 0; i < s.size(); i++) {
        if (!s.get(i).equals(w)) next.add(s.get(i));
      }
      s = next;
    }
    setList("learned", s);
  }

  private void refreshRecent() {
    recentBox.removeAllViews();
    List<String> h = getList("history");
    if (h.size() == 0) return;
    recentBox.addView(label("RECENT"));
    int count = h.size() < 5 ? h.size() : 5;
    for (int i = 0; i < count; i++) {
      final String item = h.get(i);
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
    dirButton = btn(faDirection ? "FA -> EN" : "EN -> FA", CARD2);
    dirButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        faDirection = !faDirection;
        prefs.edit().putBoolean("dir", faDirection).apply();
        dirButton.setText(faDirection ? "FA -> EN" : "EN -> FA");
        resultBox.removeAllViews();
      }
    });
    row.addView(lookupBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    View spacer = new View(this);
    spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), LinearLayout.LayoutParams.WRAP_CONTENT));
    row.addView(spacer);
    row.addView(dirButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
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
        setList("history", new ArrayList<String>());
        refreshHistory();
        refreshRecent();
      }
    });
    screen.addView(clearBtn);
    return screen;
  }

  private void doLookup(String raw) {
    String q = raw == null ? "" : raw.replace("\n", " ").trim();
    if (q.length() == 0) {
      resultBox.removeAllViews();
      resultBox.addView(tv("Type a word first.", 15, SUB, false));
      return;
    }
    addToHistory(q);
    refreshRecent();
    lookupId++;
    final int myId = lookupId;
    if (!faDirection) {
      WordsData.Word w = WordsData.findExact(q);
      if (w != null) {
        lastWord = w;
        lastQuery = w.word;
        lastTranslation = null;
        lastKind = "word";
        resultBox.removeAllViews();
        renderWordInto(resultBox, w, false);
        return;
      }
      resultBox.removeAllViews();
      resultBox.addView(tv("No offline entry for \"" + q + "\".", 15, SUB, false));
      List<WordsData.Word> sims = WordsData.findSimilar(q, 8);
      if (sims.size() > 0) {
        resultBox.addView(label("SIMILAR WORDS"));
        for (int i = 0; i < sims.size(); i++) {
          final String sw = sims.get(i).word;
          Button b = btn(sw, CARD2);
          b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
              input.setText(sw);
              doLookup(sw);
            }
          });
          resultBox.addView(b);
        }
      }
      final TextView waiting = tv("Translating…", 15, SUB, false);
      resultBox.addView(waiting);
      final String fq = q;
      fetchTranslation(q, "auto", "fa", new TranslationCallback() {
        public void onResult(String translation) {
          if (myId != lookupId) return;
          resultBox.removeView(waiting);
          if (translation == null) {
            resultBox.addView(tv("Needs internet for this word.", 15, SUB, false));
          } else {
            lastQuery = fq;
            lastTranslation = translation;
            lastWord = null;
            lastKind = "translation";
            renderTranslationInto(resultBox, fq, translation, false);
          }
        }
      });
    } else {
      resultBox.removeAllViews();
      final TextView waiting = tv("Translating…", 15, SUB, false);
      resultBox.addView(waiting);
      final String fq = q;
      fetchTranslation(q, "fa", "en", new TranslationCallback() {
        public void onResult(String translation) {
          if (myId != lookupId) return;
          resultBox.removeView(waiting);
          if (translation == null) {
            resultBox.addView(tv("Needs internet for this word.", 15, SUB, false));
          } else {
            lastQuery = fq;
            lastTranslation = translation;
            lastWord = null;
            lastKind = "translation-fa";
            LinearLayout card = card();
            card.addView(tv(fq, 20, TEXT, true));
            card.addView(label("ENGLISH"));
            card.addView(tv(translation, 16, TEXT, false));
            resultBox.addView(card);
          }
        }
      });
    }
  }

  private void fetchTranslation(final String q, final String sl, final String tl,
      final TranslationCallback cb) {
    new Thread(new Runnable() {
      public void run() {
        try {
          String enc = URLEncoder.encode(q, "UTF-8");
          String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
              + sl + "&tl=" + tl + "&dt=t&q=" + enc;
          URL url = new URL(urlStr);
          HttpURLConnection conn = (HttpURLConnection) url.openConnection();
          conn.setConnectTimeout(8000);
          conn.setReadTimeout(8000);
          conn.setRequestProperty("User-Agent", "SearchAI/1.0");
          BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
          StringBuilder sb = new StringBuilder();
          String line;
          while ((line = r.readLine()) != null) sb.append(line);
          r.close();
          final String result = parseGtx(sb.toString());
          runOnUiThread(new Runnable() {
            public void run() {
              cb.onResult(result);
            }
          });
        } catch (Exception e) {
          runOnUiThread(new Runnable() {
            public void run() {
              cb.onResult(null);
            }
          });
        }
      }
    }).start();
  }

  private String parseGtx(String resp) {
    if (resp == null) return null;
    String marker = "[[[\"";
    int start = resp.indexOf(marker);
    if (start < 0) return null;
    int i = start + marker.length();
    StringBuilder sb = new StringBuilder();
    while (i < resp.length()) {
      char c = resp.charAt(i);
      if (c == '\\' && i + 1 < resp.length()) {
        char n = resp.charAt(i + 1);
        if (n == '"') {
          sb.append('"');
          i += 2;
          continue;
        }
        if (n == '\\') {
          sb.append('\\');
          i += 2;
          continue;
        }
        if (n == 'n') {
          sb.append(' ');
          i += 2;
          continue;
        }
        if (n == 'u' && i + 5 < resp.length()) {
          try {
            int code = Integer.parseInt(resp.substring(i + 2, i + 6), 16);
            sb.append((char) code);
          } catch (Exception e) {
          }
          i += 6;
          continue;
        }
        i += 2;
        continue;
      }
      if (c == '"') break;
      sb.append(c);
      i++;
    }
    String out = sb.toString().trim();
    return out.length() == 0 ? null : out;
  }

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
    saveBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowSaved = !isSaved(w.word);
        setSaved(w.word, nowSaved);
        saveBtn.setText(nowSaved ? "Saved" : "Save");
        saveBtn.setBackground(rounded(nowSaved ? GREEN : ACCENT, 10));
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
          showCard(w, "result");
        }
      });
      row.addView(showBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }
    card.addView(row);
    final Button learnBtn = btn(isLearned(w.word) ? "Learned" : "Mark learned",
        isLearned(w.word) ? GREEN : CARD2);
    learnBtn.setPadding(dp(14), dp(10), dp(14), dp(10));
    learnBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowLearned = !isLearned(w.word);
        setLearned(w.word, nowLearned);
        learnBtn.setText(nowLearned ? "Learned" : "Mark learned");
        learnBtn.setBackground(rounded(nowLearned ? GREEN : CARD2, 10));
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
    saveBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean nowSaved = !isSaved(query);
        setSaved(query, nowSaved);
        if (nowSaved) {
          prefs.edit().putString("trans::" + query, translation).apply();
        } else {
          prefs.edit().remove("trans::" + query).apply();
        }
        saveBtn.setText(nowSaved ? "Saved" : "Save");
        saveBtn.setBackground(rounded(nowSaved ? GREEN : ACCENT, 10));
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
          showTranslationCard(query, translation, "result");
        }
      });
      row.addView(showBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }
    card.addView(row);
    parent.addView(card);
  }

  private void refreshSaved() {
    savedList.removeAllViews();
    List<String> saved = getList("saved");
    savedCount.setText(saved.size() + " saved words");
    if (saved.size() == 0) {
      savedList.addView(tv("No saved words yet. Save a word from search.", 15, SUB, false));
      return;
    }
    for (int i = 0; i < saved.size(); i++) {
      final String item = saved.get(i);
      String labelText = item;
      if (isLearned(item)) labelText = labelText + " · Learned";
      Button b = btn(labelText, CARD2);
      b.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          openSavedWord(item);
        }
      });
      savedList.addView(b);
    }
  }

  private void openSavedWord(String item) {
    WordsData.Word w = WordsData.findExact(item);
    if (w != null) {
      showCard(w, "saved");
      return;
    }
    String trans = prefs.getString("trans::" + item, "");
    if (trans.length() > 0) {
      showTranslationCard(item, trans, "saved");
      return;
    }
    input.setText(item);
    doLookup(item);
    showScreen("search");
  }

  private void refreshHistory() {
    historyList.removeAllViews();
    List<String> h = getList("history");
    if (h.size() == 0) {
      historyList.addView(tv("No history yet.", 15, SUB, false));
      return;
    }
    for (int i = 0; i < h.size(); i++) {
      final String item = h.get(i);
      Button b = btn(item, CARD2);
      b.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          input.setText(item);
          doLookup(item);
          showScreen("search");
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
      final TextView status = tv(prefs.getBoolean("on_" + name, false) ? "On" : "Off", 14, SUB, false);
      row.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      Switch sw = new Switch(this);
      sw.setChecked(prefs.getBoolean("on_" + name, false));
      sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          prefs.edit().putBoolean("on_" + name, isChecked).apply();
          status.setText(isChecked ? "On" : "Off");
        }
      });
      row.addView(sw);
      card.addView(row);
      boolean isSelected = selected.equals(name);
      Button useBtn = btn(isSelected ? "Selected for answers" : "Use for answers",
          isSelected ? GREEN : CARD2);
      useBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          prefs.edit().putString("provider", name).apply();
          refreshSettings();
        }
      });
      card.addView(useBtn);
      settingsBox.addView(card);
    }
    settingsBox.addView(tv("Search AI by SobhaN", 14, SUB, false));
    settingsBox.addView(tv("Version 1", 14, SUB, false));
    settingsBox.addView(tv("Saved words and history stay on this phone.", 14, SUB, false));
  }

  private Button backButton() {
    Button back = btn("Back", CARD2);
    back.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        if (cardSource.equals("saved")) {
          showScreen("saved");
          return;
        }
        if (lastKind.equals("word") && lastWord != null) {
          resultBox.removeAllViews();
          renderWordInto(resultBox, lastWord, false);
          showScreen("search");
          return;
        }
        if (lastKind.equals("translation") && lastQuery != null && lastTranslation != null) {
          resultBox.removeAllViews();
          renderTranslationInto(resultBox, lastQuery, lastTranslation, false);
          showScreen("search");
          return;
        }
        showScreen("search");
      }
    });
    return back;
  }

  private void showCard(WordsData.Word w, String source) {
    cardSource = source;
    cardBox.removeAllViews();
    cardBox.addView(backButton());
    renderWordInto(cardBox, w, true);
    showScreen("card");
  }

  private void showTranslationCard(String query, String translation, String source) {
    cardSource = source;
    cardBox.removeAllViews();
    cardBox.addView(backButton());
    renderTranslationInto(cardBox, query, translation, true);
    showScreen("card");
  }
}
