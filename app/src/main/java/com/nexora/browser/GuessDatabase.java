package com.nexora.browser;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class GuessDatabase extends SQLiteOpenHelper {
    private static final int VERSION = 1;

    public GuessDatabase(Context c, String network) {
        super(c, "nexora_guess_" + safe(network) + ".db", null, VERSION);
    }

    private static String safe(String s) {
        if (s == null || s.isEmpty()) return "default";
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS attempted (number TEXT PRIMARY KEY, ts DATETIME DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS found (number TEXT PRIMARY KEY, data TEXT, ts DATETIME DEFAULT CURRENT_TIMESTAMP)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}

    public boolean exists(String v) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM attempted WHERE number=? LIMIT 1", new String[]{v});
        boolean r = c.moveToFirst(); c.close(); return r;
    }
    public void addAttempt(String v) {
        try { getWritableDatabase().execSQL("INSERT OR IGNORE INTO attempted(number) VALUES (?)", new Object[]{v}); }
        catch (Exception e) {}
    }
    public int countAttempts() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM attempted", null);
        int n = 0; if (c.moveToFirst()) n = c.getInt(0); c.close(); return n;
    }
    public void addFound(String v, String data) {
        try { getWritableDatabase().execSQL("INSERT OR REPLACE INTO found(number,data) VALUES (?,?)", new Object[]{v, data}); }
        catch (Exception e) {}
    }
    public int countFound() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM found", null);
        int n = 0; if (c.moveToFirst()) n = c.getInt(0); c.close(); return n;
    }
    public void clearAll() {
        try {
            getWritableDatabase().execSQL("DELETE FROM attempted");
            getWritableDatabase().execSQL("DELETE FROM found");
        } catch (Exception e) {}
    }
}
