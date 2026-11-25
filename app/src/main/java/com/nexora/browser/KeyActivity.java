package com.nexora.browser;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class KeyActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key);

        TextView statusText = findViewById(R.id.keyStatus);
        TextView timerText = findViewById(R.id.keyTimer);
        EditText input = findViewById(R.id.etKey);
        Button btnActivate = findViewById(R.id.btnActivate);
        Button btnContinue = findViewById(R.id.btnContinue);

        if (KeyManager.isActivated(this)) {
            statusText.setText("✅ التطبيق مفعّل");
            timerText.setText("الوقت المتبقي: " + KeyManager.formatRemaining(this));
            input.setVisibility(EditText.GONE);
            btnActivate.setVisibility(Button.GONE);
            btnContinue.setVisibility(Button.VISIBLE);
            btnContinue.setOnClickListener(v -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        } else {
            statusText.setText("🔒 التطبيق مقفل");
            timerText.setText("أدخل مفتاح التنشيط للمتابعة");
            btnContinue.setVisibility(Button.GONE);
            btnActivate.setOnClickListener(v -> {
                String key = input.getText() == null ? "" : input.getText().toString().trim();
                if (KeyManager.activate(this, key)) {
                    Toast.makeText(this, "✅ تم التنشيط بنجاح", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                } else {
                    Toast.makeText(this, "❌ مفتاح غير صحيح", Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
