package com.stagepulse.mix;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class StagePlannerActivity extends Activity {
  private WebView web;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    web = new WebView(this);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);
    web.setWebViewClient(new WebViewClient());
    web.loadUrl("file:///android_asset/planner/index.html");
    setContentView(web);
  }
  @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
