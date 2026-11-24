package com.nexora.browser;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class CookieHelper {
    public interface Callback {
        void onResult(String username, String storedValues, String allCookies);
    }

    public static void readCookies(final Context context, final String baseUrl, final Callback cb) {
        try {
            final WebView wv = new WebView(context);
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

            wv.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    view.evaluateJavascript("document.cookie", value -> {
                        String raw = value != null ? value : "";
                        if (raw.startsWith("\"") && raw.endsWith("\"")) {
                            raw = raw.substring(1, raw.length() - 1);
                        }
                        raw = raw.replace("\\\"", "\"").replace("\\u003D", "=").replace("\\u0026", "&");

                        String username = null;
                        String stored = null;
                        for (String pair : raw.split(";\\s*")) {
                            int eq = pair.indexOf('=');
                            if (eq < 0) continue;
                            String key = pair.substring(0, eq).trim();
                            String val = pair.substring(eq + 1).trim();
                            if (key.equals("username")) username = val;
                            else if (key.equals("storedValues")) {
                                try { stored = java.net.URLDecoder.decode(val, "UTF-8"); }
                                catch (Exception e) { stored = val; }
                            }
                        }
                        cb.onResult(username, stored, raw);
                    });
                }
            });
            wv.loadUrl(baseUrl);
        } catch (Exception e) {
            cb.onResult(null, null, "ERR: " + e.getMessage());
        }
    }
}
