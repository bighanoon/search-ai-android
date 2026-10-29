package com.sobhan.searchai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/** Small line-drawn icons in the web's thin style. Drawn with Canvas,
    never emoji, so they match the web icons at any size. */
public class IconView extends View {
  public static final int SPEAKER = 0;
  public static final int CLOCK = 1;
  public static final int GEAR = 2;
  public static final int BOOKMARK = 3;
  public static final int IMAGE = 4;
  public static final int SHARE = 5;
  public static final int ADD = 6;
  public static final int HEART = 7;
  public static final int SWAP = 8;
  public static final int CHECK = 9;

  private int kind = SPEAKER;
  private int color = 0xFFFFFFFF;
  private boolean filled = false;
  private Paint paint;
  private Paint fillPaint;

  public IconView(Context context) {
    super(context);
    init();
  }

  public IconView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    fillPaint.setStyle(Paint.Style.FILL);
  }

  public void setKind(int kind) {
    this.kind = kind;
    invalidate();
  }

  public void setIconColor(int color) {
    this.color = color;
    invalidate();
  }

  public void setFilled(boolean filled) {
    this.filled = filled;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float s = Math.min(getWidth(), getHeight()) / 100f;
    if (s <= 0) return;
    canvas.translate((getWidth() - 100 * s) / 2f,
        (getHeight() - 100 * s) / 2f);
    canvas.scale(s, s);
    paint.setColor(color);
    paint.setStrokeWidth(7);
    fillPaint.setColor(color);
    switch (kind) {
      case SPEAKER: drawSpeaker(canvas); break;
      case CLOCK: drawClock(canvas); break;
      case GEAR: drawGear(canvas); break;
      case BOOKMARK: drawBookmark(canvas); break;
      case IMAGE: drawImage(canvas); break;
      case SHARE: drawShare(canvas); break;
      case ADD: drawAdd(canvas); break;
      case HEART: drawHeart(canvas); break;
      case SWAP: drawSwap(canvas); break;
      case CHECK: drawCheck(canvas); break;
      default: break;
    }
  }

  private void drawSpeaker(Canvas c) {
    Path p = new Path();
    p.moveTo(12, 38);
    p.lineTo(42, 38);
    p.lineTo(72, 18);
    p.lineTo(72, 82);
    p.lineTo(42, 62);
    p.lineTo(12, 62);
    p.close();
    c.drawPath(p, filled ? fillPaint : paint);
    if (!filled) {
      c.drawArc(74, 32, 92, 68, -45, 90, false, paint);
    }
  }

  private void drawClock(Canvas c) {
    c.drawCircle(50, 50, 36, paint);
    c.drawLine(50, 50, 50, 28, paint);
    c.drawLine(50, 50, 66, 58, paint);
  }

  private void drawGear(Canvas c) {
    for (int i = 0; i < 8; i++) {
      c.save();
      c.rotate(i * 45, 50, 50);
      c.drawRect(44, 6, 56, 22, filled ? fillPaint : paint);
      c.restore();
    }
    c.drawCircle(50, 50, 30, paint);
    c.drawCircle(50, 50, 12, paint);
  }

  private void drawBookmark(Canvas c) {
    Path p = new Path();
    p.moveTo(28, 12);
    p.lineTo(72, 12);
    p.lineTo(72, 88);
    p.lineTo(50, 68);
    p.lineTo(28, 88);
    p.close();
    c.drawPath(p, filled ? fillPaint : paint);
  }

  private void drawImage(Canvas c) {
    c.drawRect(14, 22, 86, 78, paint);
    c.drawCircle(32, 38, 7, filled ? fillPaint : paint);
    Path p = new Path();
    p.moveTo(14, 78);
    p.lineTo(42, 48);
    p.lineTo(60, 66);
    p.lineTo(74, 50);
    p.lineTo(86, 64);
    c.drawPath(p, paint);
  }

  private void drawShare(Canvas c) {
    c.drawLine(30, 68, 68, 34, paint);
    c.drawLine(30, 68, 68, 74, paint);
    c.drawCircle(26, 70, 9, filled ? fillPaint : paint);
    c.drawCircle(72, 30, 9, filled ? fillPaint : paint);
    c.drawCircle(72, 76, 9, filled ? fillPaint : paint);
  }

  private void drawAdd(Canvas c) {
    c.drawRect(18, 18, 82, 82, paint);
    c.drawLine(50, 34, 50, 66, paint);
    c.drawLine(34, 50, 66, 50, paint);
  }

  private void drawHeart(Canvas c) {
    Path p = new Path();
    p.moveTo(50, 86);
    p.cubicTo(20, 60, 12, 40, 26, 26);
    p.cubicTo(38, 16, 50, 26, 50, 38);
    p.cubicTo(50, 26, 62, 16, 74, 26);
    p.cubicTo(88, 40, 80, 60, 50, 86);
    p.close();
    c.drawPath(p, filled ? fillPaint : paint);
  }

  private void drawSwap(Canvas c) {
    c.drawLine(16, 36, 78, 36, paint);
    c.drawLine(66, 26, 80, 36, paint);
    c.drawLine(66, 46, 80, 36, paint);
    c.drawLine(84, 64, 22, 64, paint);
    c.drawLine(34, 54, 20, 64, paint);
    c.drawLine(34, 74, 20, 64, paint);
  }

  private void drawCheck(Canvas c) {
    c.drawLine(22, 52, 42, 72, paint);
    c.drawLine(42, 72, 80, 28, paint);
  }
}
