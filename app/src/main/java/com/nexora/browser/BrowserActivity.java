package com.nexora.browser;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class BrowserActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    private WebView webView;
    private EditText urlInput;
    private ProgressBar progress;
    private ImageButton btnBack, btnForward, btnReload;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        webView   = findViewById(R.id.webView);
        urlInput  = findViewById(R.id.urlInput);
        progress  = findViewById(R.id.progressBar);
        btnBack   = findViewById(R.id.btnBack);
        btnForward= findViewById(R.id.btnForward);
        btnReload = findViewById(R.id.btnReload);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString(s.getUserAgentString() + " NexoraBrowser/1.0");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                v.loadUrl(req.getUrl().toString());
                return true;
            }
            @Override
            public void onPageStarted(WebView v, String url, Bitmap fav) {
                progress.setVisibility(View.VISIBLE);
                urlInput.setText(url);
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                progress.setVisibility(View.GONE);
                if (!url.startsWith("about:")) urlInput.setText(url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progress.setProgress(p);
                progress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try {
                    String name = URLUtil.guessFileName(url, cd, mime);
                    DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                    r.setMimeType(mime);
                    r.addRequestHeader("User-Agent", ua);
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) dm.enqueue(r);
                    Toast.makeText(BrowserActivity.this, "بدأ التنزيل: " + name, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(BrowserActivity.this, "فشل التنزيل: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        btnReload.setOnClickListener(v -> webView.reload());
        findViewById(R.id.btnHome).setOnClickListener(v -> finish());

        urlInput.setOnEditorActionListener((TextView tv, int actionId, KeyEvent ev) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String q = urlInput.getText().toString().trim();
                if (!q.isEmpty()) navigate(q);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        String initial = getIntent().getStringExtra(EXTRA_URL);
        if (initial == null || initial.isEmpty()) initial = "https://www.google.com";
        navigate(initial);
    }

    private void navigate(String input) {
        String url;
        if (input.matches("^[a-zA-Z]+://.*")) url = input;
        else if (input.contains(".") && !input.contains(" ")) url = "https://" + input;
        else url = "https://www.google.com/search?q=" + Uri.encode(input);
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
