package com.sobhan.searchai;

/** v9/v10, Android-free: the result block header. Before v9 the
    result block started with a large duplicate word heading (for
    example "night") plus an X above the tabs; that heading
    duplicated the word already shown in the search header and in
    the word card, so v9 removed it, leaving only a small X close
    control at the top-right. v10 removes the X as well: the result
    block now starts directly with the tabs row, and system Back on
    a result clears the result instead (see backClearsResult), so
    the result page is never left without a way back to home.
    Clearing empties the result box, which restores the home
    sections through ResultPageSections. This class takes no word
    or saved-data input, so the header change can never alter
    saved words: it only describes the header composition and the
    close outcome. */
public class ResultHeader {

  /** v9: the result block shows no duplicate word heading. */
  public static boolean hasWordHeading() {
    return false;
  }

  /** v10: no X close control remains; the block starts with tabs. */
  public static boolean hasCloseControl() {
    return false;
  }

  /** v10: pressing system Back on a result clears the result. */
  public static boolean backClearsResult() {
    return true;
  }

  /** Result child count after Back clears the result: box emptied. */
  public static int childCountAfterClose() {
    return 0;
  }
}
