package com.nexora.browser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

public class KeyManager {
    public static final String KEY_PERM = "0.F_YX-PERM-NEXORA-VIP-2025";
    public static final String KEY_4D = "0.F_YX-4D-NEXORA-TRIAL-2025";
    public static final String KEY_10D = "0.F_YX-10D-NEXORA-PRO-2025";

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final String PREFS = "nexora_key";
    private static final String CHANNEL_ID = "nexora_key_exp";

    public static boolean activate(Context ctx, String inputKey) {
        inputKey = inputKey == null ? "" : inputKey.trim();
        long duration;
        String type;
        if (inputKey.equals(KEY_PERM)) { duration = -1; type = "perm"; }
        else if (inputKey.equals(KEY_4D)) { duration = 4 * DAY_MS; type = "4d"; }
        else if (inputKey.equals(KEY_10D)) { duration = 10 * DAY_MS; type = "10d"; }
        else return false;

        long now = System.currentTimeMillis();
        long exp = (duration < 0) ? Long.MAX_VALUE : (now + duration);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("type", type)
            .putLong("activated_at", now)
            .putLong("expires_at", exp)
            .apply();
        return true;
    }

    public static boolean isActivated(Context ctx) {
        long exp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("expires_at", 0);
        return exp != 0 && System.currentTimeMillis() < exp;
    }

    public static boolean isPermanent(Context ctx) {
        return "perm".equals(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("type", ""));
    }

    public static long getRemainingMs(Context ctx) {
        long exp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("expires_at", 0);
        return Math.max(0, exp - System.currentTimeMillis());
    }

    public static String formatRemaining(Context ctx) {
        if (isPermanent(ctx)) return "دائم ✓";
        long ms = getRemainingMs(ctx);
        if (ms <= 0) return "منتهي";
        long days = ms / DAY_MS;
        long hours = (ms % DAY_MS) / (60L * 60 * 1000);
        long minutes = (ms % (60L * 60 * 1000)) / (60L * 1000);
        if (days > 0) return days + " يوم و " + hours + " ساعة";
        if (hours > 0) return hours + " ساعة و " + minutes + " دقيقة";
        return minutes + " دقيقة";
    }

    public static void checkExpirationNotification(Context ctx) {
        if (isPermanent(ctx)) return;
        long ms = getRemainingMs(ctx);
        if (ms <= 0 || ms > DAY_MS) return;
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastNotif = p.getLong("last_notif", 0);
        if (System.currentTimeMillis() - lastNotif < DAY_MS) return;
        p.edit().putLong("last_notif", System.currentTimeMillis()).apply();

        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Key Expiry", NotificationManager.IMPORTANCE_HIGH));
                nb = new Notification.Builder(ctx, CHANNEL_ID);
            } else {
                nb = new Notification.Builder(ctx);
            }
            nb.setContentTitle("⚠️ ينتهي المفتاح قريباً")
              .setContentText("الوقت المتبقي: " + formatRemaining(ctx) + " — راجع قناة التلجرام")
              .setSmallIcon(android.R.drawable.ic_dialog_alert)
              .setAutoCancel(true);
            nm.notify(99, nb.build());
        } catch (Exception e) {}
    }
}
