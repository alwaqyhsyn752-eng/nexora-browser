package com.nexora.browser;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class GuessDatabase extends SQLiteOpenHelper {
    private static final int VERSION = 2;

    public GuessDatabase(Context c, String network) {
        super(c, "nexora_guess_" + safe(network) + ".db", null, VERSION);
    }

    private static String safe(String s) {
        if (s == null || s.isEmpty()) return "default";
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS attempted (" +
            "number TEXT PRIMARY KEY, status TEXT DEFAULT 'pending', " +
            "data TEXT, ts DATETIME DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS found (" +
            "number TEXT PRIMARY KEY, data TEXT, " +
            "ts DATETIME DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS config (" +
            "key TEXT PRIMARY KEY, value TEXT)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) {
            try { db.execSQL("ALTER TABLE attempted ADD COLUMN status TEXT DEFAULT 'pending'"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE attempted ADD COLUMN data TEXT"); } catch (Exception e) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS config (key TEXT PRIMARY KEY, value TEXT)"); } catch (Exception e) {}
            try { db.execSQL("UPDATE attempted SET status='success' WHERE number IN (SELECT number FROM found)"); } catch (Exception e) {}
        }
    }

    // === Config (البادئة واللاحقة لكل شبكة) ===
    public String getConfig(String key) {
        try {
            Cursor c = getReadableDatabase().rawQuery(
                "SELECT value FROM config WHERE key=?", new String[]{key});
            String r = null;
            if (c.moveToFirst()) r = c.getString(0);
            c.close();
            return r;
        } catch (Exception e) { return null; }
    }

    public void saveConfig(String key, String value) {
        try {
            getWritableDatabase().execSQL(
                "INSERT OR REPLACE INTO config(key,value) VALUES (?,?)",
                new Object[]{key, value});
        } catch (Exception e) {}
    }

    // === Attempts ===
    public boolean exists(String v) {
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT 1 FROM attempted WHERE number=? LIMIT 1", new String[]{v});
        boolean r = c.moveToFirst();
        c.close();
        return r;
    }

    public void addAttempt(String v) {
        addAttemptWithStatus(v, "pending", null);
    }

    public void addAttemptWithStatus(String v, String status, String data) {
        try {
            getWritableDatabase().execSQL(
                "INSERT OR REPLACE INTO attempted(number,status,data) VALUES (?,?,?)",
                new Object[]{v, status, data});
        } catch (Exception e) {}
    }

    public int countAttempts() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM attempted", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public void addFound(String v, String data) {
        try {
            getWritableDatabase().execSQL(
                "INSERT OR REPLACE INTO found(number,data) VALUES (?,?)",
                new Object[]{v, data});
        } catch (Exception e) {}
        addAttemptWithStatus(v, "success", data);
    }

    public int countFound() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM found", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    // === الملفات (تصفية حسب الحالة) ===
    public List<String> getByStatus(String status) {
        List<String> list = new ArrayList<>();
        try {
            Cursor c = getReadableDatabase().rawQuery(
                "SELECT number FROM attempted WHERE status=? ORDER BY ts DESC",
                new String[]{status});
            while (c.moveToNext()) list.add(c.getString(0));
            c.close();
        } catch (Exception e) {}
        return list;
    }

    public void clearAll() {
        try {
            getWritableDatabase().execSQL("DELETE FROM attempted");
            getWritableDatabase().execSQL("DELETE FROM found");
            getWritableDatabase().execSQL("DELETE FROM config");
        } catch (Exception e) {}
    }
}
