package com.sobhan.searchai;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Minimal read-only content provider that serves the word files the
    app writes to its own cache, so the Android share sheet can hand
    one file to another app (Downloads, Telegram, WhatsApp, Drive,
    email) with no storage permission. No library is needed: the
    provider serves only files under cache/wordfiles, rejects any
    name containing a path separator, and grants read access only to
    the single URI the share intent carries. */
public class WordFileProvider extends ContentProvider {

  public static final String AUTHORITY = "com.sobhan.searchai.fileprovider";

  @Override
  public boolean onCreate() {
    return true;
  }

  /** The cache folder word files live in. */
  public static File wordFilesDir(android.content.Context ctx) {
    File dir = new File(ctx.getCacheDir(), "wordfiles");
    if (!dir.exists()) dir.mkdirs();
    return dir;
  }

  private File fileForUri(Uri uri) throws FileNotFoundException {
    String name = uri.getLastPathSegment();
    if (name == null || name.length() == 0
        || name.contains("/") || name.contains("\\")
        || name.equals(".") || name.equals("..")) {
      throw new FileNotFoundException("bad word file name");
    }
    File dir = wordFilesDir(getContext());
    File f = new File(dir, name);
    if (!f.isFile()) throw new FileNotFoundException("no such word file");
    return f;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode)
      throws FileNotFoundException {
    if (mode != null && !mode.equals("r") && !mode.equals("rt")) {
      throw new FileNotFoundException("read-only word files");
    }
    return ParcelFileDescriptor.open(fileForUri(uri),
        ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection,
      String[] selectionArgs, String sortOrder) {
    MatrixCursor cursor = new MatrixCursor(
        new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
    try {
      File f = fileForUri(uri);
      cursor.addRow(new Object[] { f.getName(), Long.valueOf(f.length()) });
    } catch (FileNotFoundException e) {
      // empty cursor: the file is gone
    }
    return cursor;
  }

  @Override
  public String getType(Uri uri) {
    return "application/json";
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return null;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection,
      String[] selectionArgs) {
    return 0;
  }
}
