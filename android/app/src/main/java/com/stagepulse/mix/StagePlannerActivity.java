package com.stagepulse.mix;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class StagePlannerActivity extends Activity {
  private WebView web;
  private AppUpdater updater;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    web = new WebView(this);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);
    web.setWebViewClient(new WebViewClient());
    web.addJavascriptInterface(new PrintBridge(this), "AndroidPrint");
    web.loadUrl("file:///android_asset/planner/index.html");
    setContentView(web);
    updater = new AppUpdater(this);
    updater.check();
  }

  @Override protected void onResume() {
    super.onResume();
    if (updater != null) updater.onResume();
  }

  @Override protected void onDestroy() {
    if (web != null) web.destroy();
    super.onDestroy();
  }

  @Override public void onBackPressed() {
    if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
  }

  private static final class PrintBridge {
    private final Activity activity;
    PrintBridge(Activity activity) { this.activity = activity; }

    @JavascriptInterface
    public void print() {
      activity.runOnUiThread(() -> {
        WebView view = ((StagePlannerActivity) activity).web;
        if (view == null) return;
        PrintManager manager = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
        if (manager == null) return;
        PrintAttributes attributes = new PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build();
        manager.print("StagePulse Plot Rider", view.createPrintDocumentAdapter("StagePulse Plot Rider"), attributes);
      });
    }
  }
}
