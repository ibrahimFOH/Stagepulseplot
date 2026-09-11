package com.stagepulse.mix;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
  private WebView web; private EditText url; private SharedPreferences prefs;
  @Override public void onCreate(Bundle b){ super.onCreate(b); setContentView(R.layout.activity_main);
    web=findViewById(R.id.web); url=findViewById(R.id.backendUrl); Button open=findViewById(R.id.open);
    prefs=getSharedPreferences("stagepulsemix",MODE_PRIVATE); url.setText(prefs.getString("backend","http://192.168.1.10:8787"));
    WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
    web.setWebViewClient(new WebViewClient()); open.setOnClickListener(v->load()); load();
  }
  private void load(){ String u=url.getText().toString().trim(); if(!u.matches("^https?://.*")) u="http://"+u; prefs.edit().putString("backend",u).apply(); web.loadUrl(u); }
  @Override public void onBackPressed(){ if(web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
