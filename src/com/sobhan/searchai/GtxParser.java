package com.sobhan.searchai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Android-free translation parsing (org.json ships in the framework).

    GtxParser never touches Android classes, so it can be unit-tested on
    the JVM against the real org.json jar. */
public class GtxParser {
  public static final int OK = 0;
  public static final int EMPTY = 1;
  public static final int OFFLINE = 2;
  public static final int HTTP_ERROR = 3;

  public static class Result {
    public int status;
    public String translation;
    public Result(int status, String translation) {
      this.status = status;
      this.translation = translation;
    }
  }

  /** Parse a gtx (translate.googleapis.com) response.

      Concatenates every non-null first element of the segment array.
      Returns EMPTY for malformed input, an empty result, or an echo of
      the query (gtx returns the input unchanged for misspellings). */
  public static Result parseGtx(String resp, String query) {
    if (resp == null) return new Result(EMPTY, null);
    try {
      JSONArray root = new JSONArray(resp);
      JSONArray first = root.optJSONArray(0);
      if (first == null) return new Result(EMPTY, null);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < first.length(); i++) {
        JSONArray seg = first.optJSONArray(i);
        if (seg == null) continue;
        Object o = seg.opt(0);
        if (o == null || o == JSONObject.NULL) continue;
        sb.append(o.toString());
      }
      String out = sb.toString().trim();
      if (out.length() == 0) return new Result(EMPTY, null);
      if (query != null && out.equalsIgnoreCase(query.trim())) {
        return new Result(EMPTY, null);
      }
      return new Result(OK, out);
    } catch (JSONException e) {
      return new Result(EMPTY, null);
    }
  }

  /** Parse a MyMemory response. Returns null on any failure or echo. */
  public static String parseMyMemory(String resp, String query) {
    if (resp == null) return null;
    try {
      JSONObject root = new JSONObject(resp);
      if (root.optInt("responseStatus", 0) != 200) return null;
      JSONObject data = root.optJSONObject("responseData");
      if (data == null) return null;
      Object o = data.opt("translatedText");
      if (o == null || o == JSONObject.NULL) return null;
      String out = o.toString().trim();
      if (out.length() == 0) return null;
      if (query != null && out.equalsIgnoreCase(query.trim())) return null;
      return out;
    } catch (JSONException e) {
      return null;
    }
  }
}
