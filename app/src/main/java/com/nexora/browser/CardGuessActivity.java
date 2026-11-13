package com.nexora.browser;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
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
    private Button btnStart, btnStop, btnClear;
    private ScrollView formScroll;
    private LinearLayout blackPanel;
    private TextView currentCardBig, statusLine, foundDisplay;
    private Button btnStopBig, btnBack;

    private int waitMs = 500;
    private int parallelWorkers = 3;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            setContentView(R.layout.activity_card_guess);
            bindAll();
            setupSeekbars();
            wireButtons();
            loadPrefs();
        } catch (Throwable t) {
            reportCrash(t);
        }
    }

    private void reportCrash(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append((char)10);
        for (StackTraceElement e : t.getStackTrace()) {
            sb.append(e.toString()).append((char)10);
            if (sb.length() > 3500) break;
        }
        String msg = sb.toString();
        android.util.Log.e("NEXORA_CRASH", msg, t);
        try {
            java.io.File d = getExternalFilesDir(null);
            if (d != null) {
                java.io.File f = new java.io.File(d, "crash.txt");
                java.io.PrintWriter w = new java.io.PrintWriter(f);
                w.print(msg);
                w.close();
            }
        } catch (Throwable ignore) {}
        try {
            android.widget.Toast.makeText(this, "CRASH: " + t.getClass().getSimpleName() + ": " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        } catch (Throwable ignore) {}
    }

    private void bindAll() {
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
    }

    private void setupSeekbars() {
        seekWait.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                waitMs = Math.max(100, p * 100);
                lblWait.setText("الانتظار: " + (waitMs / 1000f) + " ثانية");
            }
        });
        seekParallel.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                parallelWorkers = Math.max(1, p);
                lblParallel.setText("التوازي (عمال): " + parallelWorkers);
            }
        });
    }

    private void wireButtons() {
        btnStart.setOnClickListener(v -> startGuessing());
        btnStop.setOnClickListener(v -> stopGuessing());
        btnStopBig.setOnClickListener(v -> stopGuessing());
        btnBack.setOnClickListener(v -> stopGuessing());
        btnClear.setOnClickListener(v -> confirmClear());
    }

    private void startGuessing() {
        String url = val(etNetUrl);
        String name = val(etNetName);
        if (url.isEmpty()) { toast("أدخل عنوان URL للشبكة"); return; }
        if (name.isEmpty()) name = "default";

        savePrefs();

        formScroll.setVisibility(View.GONE);
        blackPanel.setVisibility(View.VISIBLE);
        currentCardBig.setText("...");
        statusLine.setText("جاري التجهيز...");
        foundDisplay.setText("لم يتم العثور على كرت بعد");

        btnStop.setEnabled(true);
        btnStart.setEnabled(false);

        // TODO الخطوة التالية: NetworkFinder
        toast("منطق التخمين سيُفعّل في الخطوة التالية");
    }

    private void stopGuessing() {
        formScroll.setVisibility(View.VISIBLE);
        blackPanel.setVisibility(View.GONE);
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        savePrefs();
    }

    private void confirmClear() {
        String name = val(etNetName);
        if (name.isEmpty()) name = "default";
        final String fname = name;
        new android.app.AlertDialog.Builder(this)
            .setTitle("مسح محتوى التخمين")
            .setMessage("سيتم مسح كل الأرقام المحفوظة للشبكة: " + fname)
            .setPositiveButton("نعم، امسح", (d, w) -> {
                new GuessDatabase(this, fname).clearAll();
                toast("تم المسح");
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private String val(EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }
    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    private void savePrefs() {
        String name = val(etNetName); if (name.isEmpty()) name = "default";
        SharedPreferences p = getSharedPreferences("nexora_guess_" + name, MODE_PRIVATE);
        p.edit()
            .putString("url", val(etNetUrl))
            .putString("start", val(etCardStart))
            .putString("middle", val(etCardMiddle))
            .putString("end", val(etCardEnd))
            .putInt("varDigits", parseInt(val(etCardVarDigits), 1))
            .putInt("maxAttempts", parseInt(val(etCardConverters), 100000))
            .putInt("waitMs", waitMs)
            .putInt("parallel", parallelWorkers)
            .putBoolean("relax", cbRelax.isChecked())
            .apply();
    }

    private void loadPrefs() {
        String name = val(etNetName); if (name.isEmpty()) name = "default";
        SharedPreferences p = getSharedPreferences("nexora_guess_" + name, MODE_PRIVATE);
        etNetUrl.setText(p.getString("url", ""));
        etCardStart.setText(p.getString("start", ""));
        etCardMiddle.setText(p.getString("middle", ""));
        etCardEnd.setText(p.getString("end", ""));
        etCardVarDigits.setText(String.valueOf(p.getInt("varDigits", 1)));
        etCardConverters.setText(String.valueOf(p.getInt("maxAttempts", 100000)));
        waitMs = p.getInt("waitMs", 500);
        parallelWorkers = p.getInt("parallel", 3);
        cbRelax.setChecked(p.getBoolean("relax", false));
        seekWait.setProgress(waitMs / 100);
        seekParallel.setProgress(parallelWorkers);
        lblWait.setText("الانتظار: " + (waitMs / 1000f) + " ثانية");
        lblParallel.setText("التوازي (عمال): " + parallelWorkers);
    }

    private static abstract class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar sb) {}
        @Override public void onStopTrackingTouch(SeekBar sb) {}
    }
}
