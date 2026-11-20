package com.nexora.browser;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private static final String UPDATE_JSON_URL = 
        "https://raw.githubusercontent.com/alwaqyhsyn752-eng/nexora-browser/main/update.json";

    public static void check(final Context context) {
        new Thread(() -> {
            try {
                URL url = new URL(UPDATE_JSON_URL);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                c.disconnect();

                JSONObject o = new JSONObject(sb.toString());
                int latestCode = o.optInt("version_code", 0);
                String apkUrl = o.optString("apk_url", "");
                String message = o.optString("message", "تحديث جديد متوفر");

                int currentCode = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionCode;

                if (latestCode > currentCode && !apkUrl.isEmpty()) {
                    SharedPreferences prefs = context.getSharedPreferences("nexora", Context.MODE_PRIVATE);
                    int lastPrompted = prefs.getInt("last_update_prompted", 0);
                    if (lastPrompted >= latestCode) return;
                    prefs.edit().putInt("last_update_prompted", latestCode).apply();

                    final String fUrl = apkUrl;
                    final String fMsg = message;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        new AlertDialog.Builder(context)
                            .setTitle("🚀 تحديث جديد متوفر")
                            .setMessage(fMsg)
                            .setPositiveButton("تحديث الآن", (d, w) -> {
                                try {
                                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fUrl)));
                                } catch (Exception e) {}
                            })
                            .setNegativeButton("لاحقاً", null)
                            .show();
                    });
                }
            } catch (Exception e) {
                // تجاهل الأخطاء بصمت
            }
        }).start();
    }
}
