package com.nexora.browser;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    private boolean navigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        NexoraSplashView sv = findViewById(R.id.splashView);
        TextView skip = findViewById(R.id.skipBtn);
        sv.setOnAnimationEnd(new NexoraSplashView.OnAnimationEnd() {
            @Override public void onFinished() { goToMain(); }
        });
        skip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goToMain(); }
        });
    }

    private void goToMain() {
        if (navigated) return;
        navigated = true;
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
