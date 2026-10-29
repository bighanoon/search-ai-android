package com.sobhan.searchai;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** A simple wrapping row layout for chips: children flow left to
    right and wrap onto the next line, like the web's chip rows. */
public class FlowLayout extends ViewGroup {
  private int gapPx;

  public FlowLayout(Context context) {
    super(context);
    float density = context.getResources().getDisplayMetrics().density;
    gapPx = (int) (8 * density + 0.5f);
  }

  public FlowLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    float density = context.getResources().getDisplayMetrics().density;
    gapPx = (int) (8 * density + 0.5f);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft()
        - getPaddingRight();
    int lineWidth = 0;
    int lineHeight = 0;
    int totalHeight = getPaddingTop() + getPaddingBottom();
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      View child = getChildAt(i);
      if (child.getVisibility() == GONE) continue;
      measureChild(child, MeasureSpec.makeMeasureSpec(width,
          MeasureSpec.AT_MOST), heightMeasureSpec);
      int cw = child.getMeasuredWidth();
      int ch = child.getMeasuredHeight();
      if (lineWidth > 0 && lineWidth + gapPx + cw > width) {
        totalHeight += lineHeight + gapPx;
        lineWidth = 0;
        lineHeight = 0;
      }
      if (lineWidth > 0) lineWidth += gapPx;
      lineWidth += cw;
      if (ch > lineHeight) lineHeight = ch;
    }
    totalHeight += lineHeight;
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
        resolveSize(totalHeight, heightMeasureSpec));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int width = r - l - getPaddingLeft() - getPaddingRight();
    int x = getPaddingLeft();
    int y = getPaddingTop();
    int lineHeight = 0;
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      View child = getChildAt(i);
      if (child.getVisibility() == GONE) continue;
      int cw = child.getMeasuredWidth();
      int ch = child.getMeasuredHeight();
      if (x > getPaddingLeft() && x + cw > getPaddingLeft() + width) {
        x = getPaddingLeft();
        y += lineHeight + gapPx;
        lineHeight = 0;
      }
      child.layout(x, y, x + cw, y + ch);
      x += cw + gapPx;
      if (ch > lineHeight) lineHeight = ch;
    }
  }
}
