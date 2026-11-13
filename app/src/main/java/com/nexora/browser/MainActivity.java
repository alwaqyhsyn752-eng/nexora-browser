package com.nexora.browser;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private TextView clockText;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (clockText != null) clockText.setText(timeFmt.format(new Date()));
            clockHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        clockText = findViewById(R.id.clockText);
        wireButtons();
        wireUrlBar();
        clockHandler.post(clockTick);
    }

    private void wireButtons() {
        bind(R.id.btnSearch, "البحث");
        bind(R.id.btnBookmarks, "المواقع المفضلة");
        bind(R.id.btnHistory, "سجل التصفح");
        bind(R.id.btnDownloads, "التنزيلات");
        bind(R.id.btnTabs, "علامات التبويب");
        bind(R.id.btnSettings, "الإعدادات");
    }

    private void bind(int id, final String label) {
        View v = findViewById(id);
        if (v != null) {
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    Toast.makeText(MainActivity.this, label + " — قادم في الخطوة التالية", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void wireUrlBar() {
        final EditText url = findViewById(R.id.urlInput);
        ImageButton go = findViewById(R.id.btnGo);
        if (go != null) {
            go.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String q = url != null && url.getText() != null ? url.getText().toString().trim() : "";
                    if (q.isEmpty()) return;
                    Toast.makeText(MainActivity.this, "سيتم فتح: " + q + " (الخطوة التالية)", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockTick);
    }
}
