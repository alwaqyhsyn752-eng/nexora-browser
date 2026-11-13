package com.nexora.browser;

import android.app.Application;
import java.io.File;
import java.io.FileWriter;

public class NexoraApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("THREAD: ").append(t.getName()).append((char) 10);
                    sb.append(e.toString()).append((char) 10);
                    for (StackTraceElement el : e.getStackTrace()) {
                        sb.append("  at ").append(el.toString()).append((char) 10);
                        if (sb.length() > 6000) break;
                    }
                    Throwable c = e.getCause();
                    if (c != null) {
                        sb.append("Caused by: ").append(c.toString()).append((char) 10);
                        for (StackTraceElement el : c.getStackTrace()) {
                            sb.append("  at ").append(el.toString()).append((char) 10);
                            if (sb.length() > 10000) break;
                        }
                    }
                    String msg = sb.toString();
                    File[] dirs = {
                        getExternalFilesDir(null),
                        getExternalCacheDir(),
                        new File("/sdcard/Android/data/com.nexora.browser/files")
                    };
                    for (File d : dirs) {
                        try {
                            if (d != null && (d.exists() || d.mkdirs())) {
                                File f = new File(d, "crash.txt");
                                FileWriter w = new FileWriter(f, false);
                                w.write(msg);
                                w.close();
                                break;
                            }
                        } catch (Throwable ignore) {}
                    }
                    android.util.Log.e("NEXORA_CRASH", msg, e);
                } catch (Throwable ignore) {}
                if (prev != null) prev.uncaughtException(t, e);
                else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(1);
                }
            }
        });
    }
}
