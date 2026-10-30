package com.sobhan.searchai;

/** v10, Android-free: the Show card popup rules. The card is a
    popup centered vertically and horizontally over a dimmed
    backdrop, not a full-height sheet and not stuck to the bottom.
    The picture panel sits on top of the card when the word has a
    picture (bundled or downloaded); a word with no picture gets
    no picture panel, never a fake picture. The Tell Me More
    button is compact, about the size of its text, not full-width.
    This class takes no word or saved-data input, so these rules
    can never alter saved words or downloaded pictures: they only
    describe the popup layout. */
public class ShowCardLayout {

  /** v10: the popup is centered in the middle of the screen. */
  public static boolean popupCentered() {
    return true;
  }

  /** v10: the popup is not a full-height sheet or bottom sheet. */
  public static boolean popupIsFullHeightSheet() {
    return false;
  }

  /** The picture panel is on top of the card when one exists. */
  public static boolean pictureOnTop() {
    return true;
  }

  /** A word with no picture gets no picture panel at all. */
  public static boolean noPicturePanelWhenNoPicture() {
    return true;
  }

  /** v10: Tell Me More is compact, about the size of its text. */
  public static boolean tellMeMoreCompact() {
    return true;
  }

  /** v10: Tell Me More is not the old full-width button. */
  public static boolean tellMeMoreFullWidth() {
    return false;
  }
}
