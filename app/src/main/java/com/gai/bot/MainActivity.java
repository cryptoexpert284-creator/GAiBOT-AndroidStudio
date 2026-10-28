package com.gai.bot;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.view.Window;

public class MainActivity extends Activity {
  @Override public void onCreate(Bundle b){ super.onCreate(b); requestWindowFeature(Window.FEATURE_NO_TITLE); getWindow().setStatusBarColor(Color.rgb(11,13,19));
    WebView w=new WebView(this); w.setBackgroundColor(Color.rgb(11,13,19));
    WebSettings s=w.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setLoadsImagesAutomatically(true); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setUserAgentString(s.getUserAgentString()+" GAiBOTAndroid/10.0");
    w.setWebViewClient(new WebViewClient()); w.loadUrl("file:///android_asset/index.html"); setContentView(w);
  }
  @Override public void onBackPressed(){ WebView w=(WebView)findViewById(android.R.id.content).findViewWithTag("gai"); if(w!=null && w.canGoBack()) w.goBack(); else super.onBackPressed(); }
}
