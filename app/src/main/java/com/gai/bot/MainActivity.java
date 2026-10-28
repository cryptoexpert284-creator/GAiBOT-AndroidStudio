package com.gai.bot;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.view.Window;

public class MainActivity extends Activity {
  private WebView w;
  @Override public void onCreate(Bundle b){
    super.onCreate(b); requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().setStatusBarColor(Color.rgb(11,13,19));
    w=new WebView(this); w.setBackgroundColor(Color.rgb(11,13,19)); w.setTag("gai");
    WebSettings s=w.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setLoadsImagesAutomatically(true); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    s.setUserAgentString(s.getUserAgentString()+" GAiBOTAndroid/11.0");
    w.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
    w.setWebViewClient(new WebViewClient()); setContentView(w);
    startService(new Intent(this, FloatingService.class));
    if(!Settings.canDrawOverlays(this)) { try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))); } catch(Exception ignored){} }
    w.loadUrl("file:///android_asset/index.html");
  }
  public class AndroidBridge {
    @JavascriptInterface public void signal(String symbol,String direction,String tp1,String tp2,String sl,String confidence){
      getSharedPreferences("gai",MODE_PRIVATE).edit().putString("symbol",symbol).putString("direction",direction).putString("tp1",tp1).putString("tp2",tp2).putString("sl",sl).putString("confidence",confidence).apply();
      sendBroadcast(new Intent("GAI_SIGNAL_UPDATE"));
    }
    @JavascriptInterface public void price(String symbol,String price){
      getSharedPreferences("gai",MODE_PRIVATE).edit().putString("symbol",symbol).putString("price",price).apply();
      sendBroadcast(new Intent("GAI_SIGNAL_UPDATE"));
    }
    @JavascriptInterface public void select(String symbol){
      getSharedPreferences("gai",MODE_PRIVATE).edit().putString("symbol",symbol).apply();
      sendBroadcast(new Intent("GAI_SELECT"));
    }
  }
  @Override protected void onDestroy(){ super.onDestroy(); }
  @Override public void onBackPressed(){
    if(w!=null && w.canGoBack()){ w.goBack(); return; }
    // Closing the app screen does NOT close GAi BOT. The foreground floating service remains active.
    moveTaskToBack(true);
  }
}
