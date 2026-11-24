package com.nexora.browser;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class WelcomeHelper {
    public static final String TELEGRAM_URL = "https://t.me/hussein077554d";
    public static final String YOUTUBE_URL = "https://www.youtube.com/@0.F_YX";
    private static final String PREFS = "nexora";
    private static final String KEY_SHOWN = "welcome_shown";

    public static void showIfNeeded(final Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SHOWN, false)) {
            // ليس أول تشغيل — فحص التذكير الشهري فقط
            checkMonthlyReminder(context);
            return;
        }
        prefs.edit().putBoolean(KEY_SHOWN, true).apply();
        prefs.edit().putLong("first_install", System.currentTimeMillis()).apply();

        View v = LayoutInflater.from(context).inflate(R.layout.dialog_welcome, null);
        final AlertDialog dlg = new AlertDialog.Builder(context)
            .setView(v)
            .setCancelable(true)
            .create();

        v.findViewById(R.id.btnTelegram).setOnClickListener(x -> {
            try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))); } catch (Exception e) {}
            dlg.dismiss();
        });
        v.findViewById(R.id.btnYoutube).setOnClickListener(x -> {
            try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(YOUTUBE_URL))); } catch (Exception e) {}
            dlg.dismiss();
        });
        v.findViewById(R.id.btnSkip).setOnClickListener(x -> dlg.dismiss());

        dlg.show();
        try { v.startAnimation(android.view.animation.AnimationUtils.loadAnimation(context, R.anim.slide_up)); } catch (Exception e) {}
    }

    private static void checkMonthlyReminder(final Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = prefs.getLong("last_reminder", 0);
        long now = System.currentTimeMillis();
        if (now - last < 30L * 24 * 60 * 60 * 1000) return;
        prefs.edit().putLong("last_reminder", now).apply();

        new AlertDialog.Builder(context)
            .setTitle("👋 هل استمتعت بالتطبيق؟")
            .setMessage("انضم لقناتنا للحصول على آخر التحديثات والدعم الفني")
            .setPositiveButton("انضم للتلجرام", (d, w) -> {
                try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))); } catch (Exception e) {}
            })
            .setNeutralButton("يوتيوب", (d, w) -> {
                try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(YOUTUBE_URL))); } catch (Exception e) {}
            })
            .setNegativeButton("لاحقاً", null)
            .show();
    }
}
