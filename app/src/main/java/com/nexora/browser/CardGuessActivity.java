package com.nexora.browser;

import android.speech.tts.TextToSpeech;

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
    private EditText etNetUrl, etNetName, etCardStart, etCardMiddle, etCardEnd, etCardVarDigits, etCardConverters, etCustomSequences;
    private EditText etPwPrefix, etPwSuffix, etPwDigits;
    private CheckBox cbPwEnabled;
    private LinearLayout layoutPassword;
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
    private TextToSpeech tts;
    private boolean ttsEnabled = false;
    private int guessMode = 2;
    private final java.util.List<String> candidateList = new java.util.ArrayList<>();
    private GuessDatabase db;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_card_guess);
            bind(); setupSeekbars(); wire(); loadPrefs();
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(new java.util.Locale("ar"));
                }
            });
            WelcomeHelper.showIfNeeded(this);
            UpdateChecker.check(this);
            try { findViewById(android.R.id.content).startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in)); } catch (Exception e) {}
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
        etCustomSequences = findViewById(R.id.etCustomSequences);
        etPwPrefix = findViewById(R.id.etPwPrefix);
        etPwSuffix = findViewById(R.id.etPwSuffix);
        etPwDigits = findViewById(R.id.etPwDigits);
        cbPwEnabled = findViewById(R.id.cbPwEnabled);
        layoutPassword = findViewById(R.id.layoutPassword);
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
                waitMs = Math.max(300, p * 100);
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
        cbPwEnabled.setOnCheckedChangeListener((b, checked) ->
            layoutPassword.setVisibility(checked ? LinearLayout.VISIBLE : LinearLayout.GONE));
        btnStart.setOnClickListener(v -> startGuessing());
        btnStop.setOnClickListener(v -> stopGuessing());
        btnStopBig.setOnClickListener(v -> stopGuessing());
        btnBack.setOnClickListener(v -> stopGuessing());
        btnClear.setOnClickListener(v -> confirmClear());
        android.widget.Button btnDetect = findViewById(R.id.btnDetect);
        if (btnDetect != null) btnDetect.setOnClickListener(v -> autoDetectNetwork());
        if (btnShowCurrent != null) btnShowCurrent.setOnClickListener(v -> showCurrentVoucher());
        android.widget.Button btnTts = findViewById(R.id.btnTts);
        if (btnTts != null) btnTts.setOnClickListener(v -> {
            ttsEnabled = !ttsEnabled;
            btnTts.setText(ttsEnabled ? "🔊 نطق: مفعّل" : "🔇 نطق: معطّل");
            if (ttsEnabled && tts != null) tts.speak("النطق مفعل", TextToSpeech.QUEUE_FLUSH, null, "v");
        });
        android.widget.Button btnMode = findViewById(R.id.btnMode);
        if (btnMode != null) btnMode.setOnClickListener(v -> {
            guessMode = (guessMode + 1) % 3;
            String[] names = {"🎲 عشوائي", "📈 تسلسلي", "🔀 مختلط"};
            btnMode.setText(names[guessMode]);
        });
        android.widget.Button btnCookie = findViewById(R.id.btnReadCookie);
        if (btnCookie != null) btnCookie.setOnClickListener(v -> readCookieAndShow());

        android.widget.Button btnInstr = findViewById(R.id.btnInstructions);
        if (btnInstr != null) btnInstr.setOnClickListener(v -> 
            startActivity(new android.content.Intent(this, InstructionsActivity.class)));
        android.widget.Button bvs = findViewById(R.id.btnViewSuccess);
        android.widget.Button bve = findViewById(R.id.btnViewExpired);
        android.widget.Button bvn = findViewById(R.id.btnViewNoResponse);
        if (bvs != null) bvs.setOnClickListener(v -> viewList("success", "الناجحة"));
        if (bve != null) bve.setOnClickListener(v -> viewList("expired", "المنتهية"));
        if (bvn != null) bvn.setOnClickListener(v -> viewList("pending", "لا رد"));
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
        String allowedDigits = "0123456789";
        try {
            if (etCustomSequences != null && etCustomSequences.getText() != null) {
                String raw = etCustomSequences.getText().toString().trim();
                if (!raw.isEmpty()) {
                    java.util.LinkedHashSet<Character> unique = new java.util.LinkedHashSet<>();
                    for (char ch : raw.toCharArray()) {
                        if (ch >= '0' && ch <= '9') unique.add(ch);
                    }
                    if (!unique.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (char ch : unique) sb.append(ch);
                        allowedDigits = sb.toString();
                    }
                }
            }
        } catch (Exception e) {}
        
        try {
            finder = new MikrotikFinder(url, net,
                    val(etCardStart), val(etCardMiddle), val(etCardEnd),
                    parseInt(val(etCardVarDigits), 1),
                    parseInt(val(etCardConverters), 100000),
                    waitMs, parallel, cbRelax.isChecked(), db,
                    allowedDigits, guessMode,
                    cbPwEnabled.isChecked(),
                    val(etPwPrefix), val(etPwSuffix),
                    parseInt(val(etPwDigits), 4));
        } catch (Exception e) { toast("خطأ: " + e.getMessage()); return; }

        savePrefs();
        startKeepAliveService();
        formScroll.setVisibility(ScrollView.GONE);
        blackPanel.setVisibility(LinearLayout.VISIBLE);
        foundDisplay.setBackgroundColor(0x00000000);
        foundDisplay.setTextColor(0xFFFFAA00);
        candidateList.clear();
        currentCardBig.setText("...");
        try { currentCardBig.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse)); } catch (Exception e) {}
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
                if (!candidateList.contains(card)) candidateList.add(card);
                StringBuilder sb = new StringBuilder("✅ المرشحون (" + candidateList.size() + "):");
                for (String c : candidateList) sb.append("\n  ").append(c);
                if (details != null) {
                    try {
                        org.json.JSONObject o = new org.json.JSONObject(details);
                        sb.append("\n\nIP: ").append(o.optString("ip", "-"));
                        sb.append("\nUptime: ").append(o.optString("uptime", "-"));
                        sb.append("\nLeft: ").append(o.optString("session_time_left", "-"));
                    } catch (Exception e) {}
                }
                sb.append("\n\n💡 جرّب كل رقم يدوياً لمعرفة الصحيح");
                foundDisplay.setText(sb.toString());
                currentCardBig.setText(card);
                Toast.makeText(CardGuessActivity.this, "وجدت: " + card, Toast.LENGTH_SHORT).show();
                showFoundNotification(card);
            }
            @Override public void onError(String msg) {
                statusLine.setText("خطأ: " + msg);
                Toast.makeText(CardGuessActivity.this, msg, Toast.LENGTH_LONG).show();
            }
            @Override public void onFinished(boolean found) {
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
                if (found) {
                    statusLine.setText("✅ توقف — تم إيجاد " + candidateList.size() + " كرت");
                    foundDisplay.setTextColor(0xFF00C853);
                } else {
                    statusLine.setText("⏹ توقف — لم يُعثر على كرت");
                }
            }
        });
    }

    private void stopGuessing() {
        if (finder != null) finder.stop();
        stopKeepAliveService();
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

    private void viewList(String status, String title) {
        if (db == null) {
            String net = val(etNetName);
            if (net.isEmpty()) net = "default";
            db = new GuessDatabase(this, net);
        }
        java.util.List<String> list = db.getByStatus(status);
        if (list.isEmpty()) { toast("لا توجد أرقام في " + title); return; }
        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(s).append("\n");
        final String content = sb.toString();
        new android.app.AlertDialog.Builder(this)
            .setTitle("📁 " + title + " (" + list.size() + ")")
            .setMessage(content)
            .setPositiveButton("نسخ الكل", (d, w) -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("vouchers", content));
                toast("تم نسخ " + list.size() + " رقم");
            })
            .setNegativeButton("إغلاق", null)
            .show();
    }

    private void readCookieAndShow() {
        String url = val(etNetUrl);
        if (url.isEmpty()) { toast("أدخل رابط الشبكة أولاً"); return; }
        String base = url.replaceAll("/[^/]*$", "");
        toast("جاري قراءة الكوكيز...");
        CookieHelper.readCookies(this, base + "/", (username, stored, all) -> runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder();
            if (username != null && !username.isEmpty()) {
                sb.append("🎫 الكرت المسجل: ").append(username).append("\n\n");
            } else {
                sb.append("❌ لا يوجد كرت مسجل في الكوكي\n\n");
            }
            if (stored != null && !stored.isEmpty()) {
                sb.append("📋 الكروت السابقة:\n");
                // storedValues = ["5808962033","..."]
                String clean = stored.replace("[", "").replace("]", "").replace("\"", "");
                if (!clean.isEmpty()) {
                    for (String s : clean.split(",")) {
                        s = s.trim();
                        if (!s.isEmpty()) sb.append("  • ").append(s).append("\n");
                    }
                }
                sb.append("\n");
            }
            sb.append("🍪 كل الكوكيز:\n").append(all);
            new android.app.AlertDialog.Builder(this)
                .setTitle("🎫 بيانات الكرت")
                .setMessage(sb.toString())
                .setPositiveButton("نسخ الكرت", (d, w) -> {
                    if (username != null && !username.isEmpty()) {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("voucher", username));
                        toast("تم نسخ الكرت: " + username);
                    } else {
                        toast("لا يوجد كرت للنسخ");
                    }
                })
                .setNeutralButton("نسخ الكل", (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("all", sb.toString()));
                    toast("تم النسخ");
                })
                .setNegativeButton("إغلاق", null)
                .show();
        }));
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (finder != null) finder.stop();
        super.onDestroy();
    }

    

    
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
            
            // 1) جرّب captive portal
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("http://connectivitycheck.gstatic.com/generate_204").openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                int code = conn.getResponseCode();
                if (code == 302 || code == 301) {
                    String loc = conn.getHeaderField("Location");
                    if (loc != null && !loc.isEmpty()) {
                        detectedUrl = loc.replaceAll("/login.*$", "");
                    }
                }
                conn.disconnect();
            } catch (Exception e) {}
            
            // 2) جرّب روابط معروفة، واقرأها لترى إذا فيها form حقيقي
            if (detectedUrl == null) {
                String[] candidates = {"http://t.net", "http://7.7.7.7",
                                       "http://10.0.0.1", "http://192.168.88.1",
                                       "http://192.168.0.1", "http://192.168.1.1",
                                       "http://1.1.1.1"};
                for (String base : candidates) {
                    try {
                        // جرّب /login
                        java.net.HttpURLConnection c1 = (java.net.HttpURLConnection)
                            new java.net.URL(base + "/login").openConnection();
                        c1.setConnectTimeout(2500);
                        c1.setReadTimeout(2500);
                        int code = c1.getResponseCode();
                        if (code == 200) {
                            byte[] buf = new byte[8000];
                            int n = c1.getInputStream().read(buf);
                            String body = new String(buf, 0, Math.max(0, n), "UTF-8");
                            if (body.contains("<form") && (body.contains("username") || body.contains("password"))) {
                                detectedUrl = base;
                                c1.disconnect();
                                break;
                            }
                        }
                        c1.disconnect();
                        
                        // جرّب index.html
                        java.net.HttpURLConnection c2 = (java.net.HttpURLConnection)
                            new java.net.URL(base + "/index.html").openConnection();
                        c2.setConnectTimeout(2500);
                        c2.setReadTimeout(2500);
                        if (c2.getResponseCode() == 200) {
                            byte[] buf = new byte[8000];
                            int n = c2.getInputStream().read(buf);
                            String body = new String(buf, 0, Math.max(0, n), "UTF-8");
                            if (body.contains("<form") && (body.contains("username") || body.contains("password"))) {
                                detectedUrl = base;
                                c2.disconnect();
                                break;
                            }
                        }
                        c2.disconnect();
                    } catch (Exception e) {}
                }
            }
            
            // 3) خطة بديلة
            if (detectedUrl == null) detectedUrl = "http://7.7.7.7";
            
            final String finalUrl = detectedUrl;
            runOnUiThread(() -> {
                etNetUrl.setText(finalUrl);
                toast("تم اكتشاف: " + finalUrl);
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
        if (etCustomSequences != null) {
        }
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

    // ========== دوال Keep-Alive والإشعارات ==========
    private void startKeepAliveService() {
        Intent serviceIntent = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopKeepAliveService() {
        Intent serviceIntent = new Intent(this, KeepAliveService.class);
        stopService(serviceIntent);
    }

    private void showFoundNotification(String card) {
        String channelId = "nexora_found";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                channelId, "Found Cards", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        Notification n;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            n = new Notification.Builder(this, channelId)
                .setContentTitle("تم العثور على بطاقة!")
                .setContentText("البطاقة: " + card)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build();
        } else {
            n = new Notification.Builder(this)
                .setContentTitle("تم العثور على بطاقة!")
                .setContentText("البطاقة: " + card)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build();
        }
        nm.notify(1001, n);
    }
    // ===============================================

}
