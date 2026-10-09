package com.clevertree.relayruntime;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/**
 * HTTP bridge for CleverScript {@code fetch()} on Android (sync from JNI).
 * Returns the same JSON shape as web {@code __relay_host_fetch}.
 */
public final class RelayRuntimeHostHttp {
    private RelayRuntimeHostHttp() {}

    public static String hostHttpFetchSync(String url, String optionsJson) {
        String method = "GET";
        try {
            if (optionsJson != null && !optionsJson.isEmpty()) {
                JSONObject o = new JSONObject(optionsJson);
                if (o.has("method")) {
                    method = o.optString("method", "GET").toUpperCase();
                }
            }
        } catch (Exception ignored) {
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(20_000);
            conn.setInstanceFollowRedirects(true);
            int status = conn.getResponseCode();
            boolean ok = status >= 200 && status < 300;
            InputStream stream = ok ? conn.getInputStream() : conn.getErrorStream();
            String text = "";
            if (stream != null) {
                byte[] buf = new byte[8192];
                StringBuilder sb = new StringBuilder();
                int n;
                while ((n = stream.read(buf)) >= 0) {
                    sb.append(new String(buf, 0, n, "UTF-8"));
                }
                text = sb.toString();
            }
            JSONObject out = new JSONObject();
            try {
                out.put("ok", ok);
                out.put("status", status);
                out.put("text", text);
            } catch (org.json.JSONException je) {
                return "{\"ok\":false,\"status\":0,\"text\":\"\",\"error\":\"json\"}";
            }
            return out.toString();
        } catch (IOException e) {
            try {
                JSONObject out = new JSONObject();
                out.put("ok", false);
                out.put("status", 0);
                out.put("text", "");
                out.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
                return out.toString();
            } catch (Exception ex) {
                return "{\"ok\":false,\"status\":0,\"text\":\"\",\"error\":\"fetch failed\"}";
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
