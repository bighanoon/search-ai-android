package com.sobhan.searchai;

import android.content.Context;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.TextView;

/** A TextView whose text the user can select, reporting each new
    non-empty selection to a listener so the app can show the
    select-to-translate popup. Button and chip labels never use this
    class (they stay plain, non-selectable TextViews).

    The listener fires only when the selected substring changes, and
    resets when the selection collapses, so re-selecting the same word
    in the same view fires again. Clearing a selection never dismisses
    the popup: the popup has its own Close, X, and outside-tap dismiss. */
public class SelTextView extends TextView {
  public interface Listener {
    void onTextSelected(SelTextView view, String text);
  }

  private Listener listener;
  private String lastReported;

  public SelTextView(Context context) {
    super(context);
    // v6: suppress the Android system selection toolbar (Copy,
    // Translate, Select all, Share bar at the top). The app's own
    // bottom popup already covers Copy and translation, so nothing
    // is lost. Returning false from onCreateActionMode keeps the
    // system bar from appearing while selection itself, and the
    // onSelectionChanged reports this class relies on, keep working.
    setCustomSelectionActionModeCallback(new ActionMode.Callback() {
      public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        return false;
      }
      public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
      }
      public boolean onActionItemClicked(ActionMode mode,
          android.view.MenuItem item) {
        return false;
      }
      public void onDestroyActionMode(ActionMode mode) {
      }
    });
  }

  public void setSelectionListener(Listener listener) {
    this.listener = listener;
  }

  /** Clears the dedup so re-selecting the same text fires again.
      Called when the popup dismisses; the framework itself clears
      the visible highlight on the outside tap. */
  public void resetSelectionReport() {
    lastReported = null;
  }

  @Override
  protected void onSelectionChanged(int selStart, int selEnd) {
    super.onSelectionChanged(selStart, selEnd);
    if (listener == null) return;
    if (selEnd <= selStart) {
      lastReported = null;
      return;
    }
    CharSequence t = getText();
    if (t == null) return;
    int s = Math.max(0, selStart);
    int e = Math.min(t.length(), selEnd);
    if (e <= s) return;
    String sel = t.subSequence(s, e).toString().trim();
    if (sel.length() == 0) return;
    if (sel.length() > 200) sel = sel.substring(0, 200).trim();
    if (sel.equals(lastReported)) return;
    lastReported = sel;
    listener.onTextSelected(this, sel);
  }
}
