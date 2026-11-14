package com.nexora.browser;

import android.app.Application;
import java.io.File;
import java.io.FileWriter;

public class NexoraApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("THREAD: ").append(t.getName()).append((char)10);
                sb.append(e.toString()).append((char)10);
                for (StackTraceElement el : e.getStackTrace()) {
                    sb.append("  at ").append(el).append((char)10);
                    if (sb.length() > 6000) break;
                }
                String msg = sb.toString();
                File[] dirs = { getExternalFilesDir(null), getCacheDir(), getFilesDir() };
                for (File d : dirs) {
                    try {
                        if (d != null && (d.exists() || d.mkdirs())) {
                            FileWriter w = new FileWriter(new File(d, "crash.txt"), false);
                            w.write(msg); w.close(); break;
                        }
                    } catch (Throwable ig) {}
                }
                android.util.Log.e("NEXORA_CRASH", msg, e);
            } catch (Throwable ig) {}
            if (prev != null) prev.uncaughtException(t, e);
            else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(1); }
        });
    }
}
