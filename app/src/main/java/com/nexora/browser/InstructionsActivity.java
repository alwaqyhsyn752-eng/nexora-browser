package com.nexora.browser;

import android.app.Activity;
import android.os.Bundle;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ScrollView;

public class InstructionsActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_instructions);
        ScrollView sv = findViewById(R.id.scrollInstructions);
        if (sv != null) {
            try { sv.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in)); } catch (Exception e) {}
        }
        Button btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }
}
