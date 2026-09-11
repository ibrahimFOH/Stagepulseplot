package com.stagepulse.mix;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.content.SharedPreferences;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
  private WebView web;
  private EditText url;
  private SharedPreferences prefs;

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    setContentView(R.layout.activity_main);
    web = findViewById(R.id.web);
    url = findViewById(R.id.backendUrl);
    Button open = findViewById(R.id.open);

    prefs = getSharedPreferences("stagepulsemix", MODE_PRIVATE);
    url.setText(prefs.getString("backend", "http://192.168.1.10:8787"));

    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
    web.setWebViewClient(new WebViewClient());

    open.setOnClickListener(v -> load());
    load();
  }

  private void load() {
    String u = url.getText().toString().trim();
    if (u.isEmpty()) {
      Toast.makeText(this, "Backend URL gerekli", Toast.LENGTH_SHORT).show();
      return;
    }
    if (!u.matches("^https?://.*")) u = "http://" + u;
    if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
    final String target = u;
    prefs.edit().putString("backend", target).apply();
    new Thread(() -> {
      int code = -1;
      try {
        HttpURLConnection c = (HttpURLConnection) new URL(target + "/health").openConnection();
        c.setConnectTimeout(2500);
        c.setReadTimeout(2500);
        c.setRequestMethod("GET");
        code = c.getResponseCode();
        c.disconnect();
      } catch (Exception ignored) {}
      final int result = code;
      runOnUiThread(() -> {
        if (result >= 200 && result < 300) {
          web.loadUrl(target);
        } else {
          Toast.makeText(this, "StagePulseMix sunucusuna ulaşılamadı: " + target, Toast.LENGTH_LONG).show();
        }
      });
    }).start();
  }

  @Override public void onBackPressed() {
    if (web.canGoBack()) web.goBack(); else super.onBackPressed();
  }
}
