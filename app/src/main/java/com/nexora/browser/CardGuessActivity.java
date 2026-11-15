package com.nexora.browser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class CardGuessActivity extends AppCompatActivity {
    private EditText etNetUrl, etNetName, etCardStart, etCardMiddle, etCardEnd, etCardVarDigits, etCardConverters;
    private SeekBar seekWait, seekParallel;
    private TextView lblWait, lblParallel;
    private CheckBox cbRelax;
    private Button btnStart, btnStop, btnClear, btnStopBig, btnBack, btnOpenRouter, btnShowCurrent;
    private ScrollView formScroll;
    private LinearLayout blackPanel;
    private TextView currentCardBig, statusLine, foundDisplay;
    private int waitMs = 500, parallel = 3;
    private PowerManager.WakeLock wakeLock;
    private static final String PREFS = "nexora_prefs";
    private static final int NOTIF_ID = 3001;
    private static final String CHANNEL_ID = "nexora_guess";
    private MikrotikFinder finder;
    private GuessDatabase db;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_card_guess);
            bind(); setupSeekbars(); wire(); loadPrefs();
        } catch (Throwable t) {
            Toast.makeText(this, "CRASH: " + t, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void bind() {
        etNetUrl = findViewById(R.id.etNetUrl);
        etNetName = findViewById(R.id.etNetName);
        etCardStart = findViewById(R.id.etCardStart);
        etCardMiddle = findViewById(R.id.etCardMiddle);
        etCardEnd = findViewById(R.id.etCardEnd);
        etCardVarDigits = findViewById(R.id.etCardVarDigits);
        etCardConverters = findViewById(R.id.etCardConverters);
        seekWait = findViewById(R.id.seekWait);
        seekParallel = findViewById(R.id.seekParallel);
        lblWait = findViewById(R.id.lblWait);
        lblParallel = findViewById(R.id.lblParallel);
        cbRelax = findViewById(R.id.cbRelax);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnClear = findViewById(R.id.btnClear);
        formScroll = findViewById(R.id.formScroll);
        blackPanel = findViewById(R.id.blackPanel);
        currentCardBig = findViewById(R.id.currentCardBig);
        statusLine = findViewById(R.id.statusLine);
        foundDisplay = findViewById(R.id.foundDisplay);
        btnStopBig = findViewById(R.id.btnStopBig);
        btnBack = findViewById(R.id.btnBack);
        btnOpenRouter = findViewById(R.id.btnOpenRouter);
        btnShowCurrent = findViewById(R.id.btnShowCurrent);
    }

    private void setupSeekbars() {
        seekWait.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                waitMs = Math.max(100, p * 100);
                lblWait.setText("الانتظار: " + (waitMs / 1000f) + " ثانية");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        seekParallel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                parallel = Math.max(1, p);
                lblParallel.setText("التوازي: " + parallel);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void wire() {
        btnStart.setOnClickListener(v -> startGuessing());
        btnStop.setOnClickListener(v -> stopGuessing());
        btnStopBig.setOnClickListener(v -> stopGuessing());
        btnBack.setOnClickListener(v -> stopGuessing());
        btnClear.setOnClickListener(v -> confirmClear());
        android.widget.Button btnDetect = findViewById(R.id.btnDetect);
        if (btnDetect != null) btnDetect.setOnClickListener(v -> autoDetectNetwork());
        if (btnShowCurrent != null) btnShowCurrent.setOnClickListener(v -> showCurrentVoucher());
        if (btnOpenRouter != null) btnOpenRouter.setOnClickListener(v -> {
            String url = val(etNetUrl);
            if (url.isEmpty()) { toast("أدخل رابط الشبكة أولاً"); return; }
            Intent i = new Intent(this, BrowserActivity.class);
            i.putExtra(BrowserActivity.EXTRA_URL, url);
            startActivity(i);
        });
    }

    private void startGuessing() {
        String url = val(etNetUrl);
        if (url.isEmpty()) { toast("أدخل رابط الشبكة"); return; }
        String net = val(etNetName); if (net.isEmpty()) net = "default";
        db = new GuessDatabase(this, net);
        try {
            finder = new MikrotikFinder(url, net,
                    val(etCardStart), val(etCardMiddle), val(etCardEnd),
                    parseInt(val(etCardVarDigits), 1),
                    parseInt(val(etCardConverters), 100000),
                    waitMs, parallel, cbRelax.isChecked(), db);
        } catch (Exception e) { toast("خطأ: " + e.getMessage()); return; }

        savePrefs();
        acquireWakeLock();
        formScroll.setVisibility(ScrollView.GONE);
        blackPanel.setVisibility(LinearLayout.VISIBLE);
        foundDisplay.setBackgroundColor(0x00000000);
        foundDisplay.setTextColor(0xFFFFAA00);
        currentCardBig.setText("...");
        statusLine.setText("المحاولات: 0");
        foundDisplay.setText("لم يُعثر على كرت بعد");
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);

        finder.start(new MikrotikFinder.Listener() {
            @Override public void onProgress(long attempts, String current) {
                statusLine.setText("المحاولات: " + attempts);
                if (current != null && !current.isEmpty()) {
                    currentCardBig.setText(current); // تحديث الرقم دائماً
                }
            }
            @Override public void onCardFound(String card, String details) {
                foundDisplay.setBackgroundColor(0xFF00C853);
                foundDisplay.setTextColor(0xFF000000);
                StringBuilder sb = new StringBuilder("✅ " + card);
                if (details != null) {
                    try {
                        org.json.JSONObject o = new org.json.JSONObject(details);
                        sb.append("\nIP: ").append(o.optString("ip", "-"));
                        sb.append("\nUptime: ").append(o.optString("uptime", "-"));
                        sb.append("\nLeft: ").append(o.optString("session_time_left", "-"));
                    } catch (Exception e) {}
                }
                foundDisplay.setText(sb.toString());
                currentCardBig.setText(card);
                Toast.makeText(CardGuessActivity.this, "وجدت: " + card, Toast.LENGTH_LONG).show();
            }
            @Override public void onError(String msg) {
                statusLine.setText("خطأ: " + msg);
                Toast.makeText(CardGuessActivity.this, msg, Toast.LENGTH_LONG).show();
            }
            @Override public void onFinished(boolean found) {
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
            }
        });
    }

    private void stopGuessing() {
        if (finder != null) finder.stop();
        releaseWakeLock();
        formScroll.setVisibility(ScrollView.VISIBLE);
        blackPanel.setVisibility(LinearLayout.GONE);
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }

    private void confirmClear() {
        String net = val(etNetName); if (net.isEmpty()) net = "default";
        final String fnet = net;
        new android.app.AlertDialog.Builder(this)
            .setTitle("مسح")
            .setMessage("مسح كل الأرقام المحفوظة للشبكة: " + fnet + "؟")
            .setPositiveButton("امسح", (d, w) -> { new GuessDatabase(this, fnet).clearAll(); toast("تم المسح"); })
            .setNegativeButton("إلغاء", null).show();
    }

    private void showCurrentVoucher() {
        String url = val(etNetUrl);
        if (url.isEmpty()) { toast("أدخل رابط الشبكة أولاً"); return; }
        toast("جاري القراءة...");
        final String u = url;
        new Thread(() -> {
            String v = MikrotikFinder.fetchCurrentVoucher(u);
            runOnUiThread(() -> {
                if (v != null) {
                    new android.app.AlertDialog.Builder(CardGuessActivity.this)
                        .setTitle("الكرت الحالي في صفحة الحالة")
                        .setMessage("الكرت: " + v)
                        .setPositiveButton("نسخ", (d, w) -> {
                            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("voucher", v));
                            toast("تم النسخ");
                        })
                        .setNegativeButton("إغلاق", null).show();
                } else {
                    toast("لا يوجد كرت مسجل حالياً (الراوتر يخفيه)");
                }
            });
        }).start();
    }

    @Override protected void onDestroy() { if (finder != null) finder.stop(); super.onDestroy(); }
    private String val(EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }
    private int parseInt(String s, int def) {
        if (s == null) return def;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '\u0660' && c <= '\u0669') sb.append((char)('0' + (c - '\u0660')));
            else sb.append(c);
        }
        try { return Integer.parseInt(sb.toString().trim()); } catch (Exception e) { return def; }
    }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    private void autoDetectNetwork() {
        toast("جاري اكتشاف بوابة الدخول...");
        new Thread(() -> {
            String detectedUrl = null;
            try {
                // طلب لموقع خارجي لاعتراض البوابة
                java.net.URL url = new java.net.URL("http://neverssl.com");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int code = conn.getResponseCode();
                
                if (code == 302 || code == 301 || code == 511) {
                    String loc = conn.getHeaderField("Location");
                    if (loc != null && !loc.isEmpty()) detectedUrl = loc;
                }
            } catch (Exception e) {}

            // إذا لم يعترض الراوتر (لأنك متصل بالفعل)، استخدم IP البوابة
            if (detectedUrl == null) {
                try {
                    android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    if (wm != null) {
                        android.net.DhcpInfo dhcp = wm.getDhcpInfo();
                        if (dhcp != null && dhcp.gateway != 0) {
                            int ip = dhcp.gateway;
                            String ipStr = String.format(java.util.Locale.US, "%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
                            detectedUrl = "http://" + ipStr + "/login"; // إضافة /login لصفحة الدخول
                        }
                    }
                } catch (Exception e) {}
            }
            
            final String finalUrl = detectedUrl;
            runOnUiThread(() -> {
                if (finalUrl != null) {
                    etNetUrl.setText(finalUrl);
                    toast("تم اكتشاف: " + finalUrl);
                } else {
                    toast("لم يتم اكتشاف البوابة. أدخل الرابط يدوياً");
                }
            });
        }).start();
    }


    private void acquireWakeLock() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null || wakeLock != null) return;
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "NEXORA::Guess");
            wakeLock.acquire(60 * 60 * 1000L);
        } catch (Throwable t) {}
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable t) {}
        wakeLock = null;
    }

    private void loadPrefs() {
        android.content.SharedPreferences p = getSharedPreferences("nexora_prefs", MODE_PRIVATE);
        etNetUrl.setText(p.getString("url", ""));
        etNetName.setText(p.getString("name", ""));
        etCardStart.setText(p.getString("start", ""));
        etCardMiddle.setText(p.getString("middle", ""));
        etCardEnd.setText(p.getString("end", ""));
        etCardVarDigits.setText(String.valueOf(p.getInt("varDigits", 1)));
        etCardConverters.setText(String.valueOf(p.getInt("maxAttempts", 100000)));
        waitMs = p.getInt("waitMs", 500);
        parallel = p.getInt("parallel", 3);
        cbRelax.setChecked(p.getBoolean("relax", false));
        seekWait.setProgress(waitMs / 100);
        seekParallel.setProgress(parallel);
        lblWait.setText("الانتظار: " + (waitMs / 1000f) + " ثانية");
        lblParallel.setText("التوازي: " + parallel);
    }

    private void savePrefs() {
        android.content.SharedPreferences p = getSharedPreferences("nexora_prefs", MODE_PRIVATE);
        p.edit()
            .putString("url", val(etNetUrl))
            .putString("name", val(etNetName))
            .putString("start", val(etCardStart))
            .putString("middle", val(etCardMiddle))
            .putString("end", val(etCardEnd))
            .putInt("varDigits", parseInt(val(etCardVarDigits), 1))
            .putInt("maxAttempts", parseInt(val(etCardConverters), 100000))
            .putInt("waitMs", waitMs)
            .putInt("parallel", parallel)
            .putBoolean("relax", cbRelax.isChecked())
            .apply();
    }

    @Override protected void onPause() { savePrefs(); super.onPause(); }
}
