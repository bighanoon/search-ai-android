package com.sobhan.searchai;

/** v8, Android-free: the rule for the My Words and Try a word
    sections on the search screen, extended in v10 with the
    result-page extras. The screen is one scrolling page (result
    box, then Recent searches, My Words, Try a word, Updating
    cards), so "result page" means the result box currently holds
    content and "home page" means it is empty. My Words and Try a
    word show on the home page and hide on the result page;
    Recent searches stay on both. v10: the tip line, the History
    and Settings pills, and the Updating cards row follow the same
    rule as My Words and Try a word (hidden on a result page,
    shown on the home page); the Updating cards download function
    itself is untouched, only its row's visibility. This class
    takes no word or saved-data input at all, so the rule can
    never change, clear, or delete saved words: it only decides
    visibility from whether a result is shown. */
public class ResultPageSections {

  /** True when a result page is shown: the result box holds content. */
  public static boolean resultShown(int resultChildCount) {
    return resultChildCount > 0;
  }

  /** True when the My Words and Try a word sections are visible. */
  public static boolean sectionsVisible(boolean resultShown) {
    return !resultShown;
  }

  /** v10: true when the result-page extras are visible: the tip
      line, the History and Settings pills, and the Updating cards
      row. Same rule as sectionsVisible, kept as its own method so
      the extras rule is tested independently. */
  public static boolean extrasVisible(boolean resultShown) {
    return !resultShown;
  }
}
