package com.gai.bot;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class FloatingService extends Service {
  WindowManager wm; View box; TextView title, price, sig, levels, status; BroadcastReceiver receiver;
  WebView engine;
  Handler handler = new Handler(Looper.getMainLooper());
  boolean destroyed = false;

  @Override public void onCreate(){
    super.onCreate();
    createForeground();
    receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
      if("GAI_SELECT".equals(i.getAction())) syncEngine(); else update();
    }};
    IntentFilter f=new IntentFilter(); f.addAction("GAI_SIGNAL_UPDATE"); f.addAction("GAI_SELECT");
    if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver,f);
    if(Settings.canDrawOverlays(this)) showBox();
    startEngine();
  }

  void createForeground(){
    if(Build.VERSION.SDK_INT>=26){
      NotificationChannel c=new NotificationChannel("gai","GAi BOT",NotificationManager.IMPORTANCE_LOW);
      c.setDescription("GAi BOT floating signal scanner");
      ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
    }
    Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"gai"):new Notification.Builder(this);
    Notification n=b.setContentTitle("GAi BOT is running").setContentText("Floating live price + signal panel is active")
      .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).setContentIntent(pi).build();
    startForeground(77,n);
  }

  void startEngine(){
    if(engine!=null)return;
    engine=new WebView(getApplicationContext());
    engine.setVisibility(View.GONE);
    WebSettings s=engine.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
    s.setLoadsImagesAutomatically(true); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    engine.addJavascriptInterface(new EngineBridge(),"AndroidBridge");
    engine.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){syncEngine();}});
    engine.loadUrl("file:///android_asset/index.html");
  }

  void syncEngine(){
    if(engine==null)return;
    String symbol=getSharedPreferences("gai",MODE_PRIVATE).getString("symbol","BTCUSDT");
    final String js="try{selectCoin('"+symbol.replace("'","")+"')}catch(e){}";
    engine.postDelayed(()->engine.evaluateJavascript(js,null),300);
  }

  class EngineBridge {
    @JavascriptInterface public void signal(String symbol,String direction,String tp1,String tp2,String sl,String confidence){
      getSharedPreferences("gai",MODE_PRIVATE).edit().putString("symbol",symbol).putString("direction",direction)
        .putString("tp1",tp1).putString("tp2",tp2).putString("sl",sl).putString("confidence",confidence).putLong("signalTs",System.currentTimeMillis()).apply();
      sendBroadcast(new Intent("GAI_SIGNAL_UPDATE"));
    }
    @JavascriptInterface public void price(String symbol,String p){
      getSharedPreferences("gai",MODE_PRIVATE).edit().putString("symbol",symbol).putString("price",p).apply();
      sendBroadcast(new Intent("GAI_SIGNAL_UPDATE"));
    }
    @JavascriptInterface public void select(String symbol){
      getSharedPreferences("gai",MODE_PRIVATE).edit().putString("symbol",symbol).apply();
      sendBroadcast(new Intent("GAI_SELECT"));
    }
  }

  void showBox(){
    if(box!=null)return;
    LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(9),dp(12),dp(9));
    GradientDrawableCompat bg=new GradientDrawableCompat(Color.rgb(20,24,35)); root.setBackground(bg.drawable);
    LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
    title=new TextView(this); title.setText("GAi BOT  •  LIVE"); title.setTextColor(Color.WHITE); title.setTextSize(12); title.setTypeface(null,1); head.addView(title,new LinearLayout.LayoutParams(0,dp(24),1));
    TextView close=new TextView(this); close.setText("×"); close.setTextColor(Color.LTGRAY); close.setTextSize(20); close.setGravity(Gravity.CENTER); head.addView(close,new LinearLayout.LayoutParams(dp(28),dp(28)));
    close.setOnClickListener(v->{stopSelf();}); root.addView(head);
    price=new TextView(this); price.setText("— USDT"); price.setTextColor(Color.WHITE); price.setTextSize(15); price.setTypeface(null,1); root.addView(price);
    status=new TextView(this); status.setText("● LIVE · SCANNING"); status.setTextColor(Color.rgb(43,215,123)); status.setTextSize(9); root.addView(status);
    sig=new TextView(this); sig.setText("SCANNING…"); sig.setTextColor(Color.rgb(255,211,77)); sig.setTextSize(19); sig.setTypeface(null,1); sig.setPadding(0,dp(4),0,dp(2)); root.addView(sig);
    levels=new TextView(this); levels.setText("TP1 —   TP2 —   SL —"); levels.setTextColor(Color.LTGRAY); levels.setTextSize(10); root.addView(levels);
    box=root;
    box.setOnClickListener(v->{Intent in=new Intent(this,MainActivity.class);in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivity(in);});
    int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
    WindowManager.LayoutParams p=new WindowManager.LayoutParams(dp(205),WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
    p.gravity=Gravity.TOP|Gravity.END; p.x=dp(7); p.y=dp(105); wm=(WindowManager)getSystemService(WINDOW_SERVICE); wm.addView(box,p); update();
  }

  void update(){
    if(sig==null)return; SharedPreferences p=getSharedPreferences("gai",MODE_PRIVATE);
    String symbol=p.getString("symbol","BTCUSDT"); String d=p.getString("direction",""); String pr=p.getString("price","");
    title.setText("GAi BOT  •  "+symbol.replace("USDT","/USDT"));
    price.setText((pr.isEmpty()?"—":pr)+" USDT");
    sig.setText(d.isEmpty()?"SCANNING…":d);
    sig.setTextColor("LONG".equals(d)?Color.rgb(43,215,123):"SHORT".equals(d)?Color.rgb(255,93,87):Color.rgb(255,211,77));
    levels.setText("TP1 "+p.getString("tp1","—")+"   TP2 "+p.getString("tp2","—")+"   SL "+p.getString("sl","—"));
    status.setText("● LIVE PRICE  ·  8 STRATEGIES  ·  10s");
  }

  int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
  @Override public int onStartCommand(Intent i,int f,int id){
    if(Settings.canDrawOverlays(this)&&box==null)showBox();
    if(engine==null)startEngine();
    return START_STICKY;
  }
  @Override public void onTaskRemoved(Intent rootIntent){
    // Keep the foreground service and floating panel alive when the app is swiped away from Recents.
    Intent restart=new Intent(getApplicationContext(),FloatingService.class);
    if(Build.VERSION.SDK_INT>=26)getApplicationContext().startForegroundService(restart); else getApplicationContext().startService(restart);
    super.onTaskRemoved(rootIntent);
  }
  @Override public void onDestroy(){destroyed=true;try{unregisterReceiver(receiver);}catch(Exception ignored){}if(box!=null&&wm!=null)try{wm.removeView(box);}catch(Exception ignored){}if(engine!=null){engine.stopLoading();engine.destroy();engine=null;}super.onDestroy();}
  @Override public android.os.IBinder onBind(Intent i){return null;}

  static class GradientDrawableCompat { android.graphics.drawable.GradientDrawable drawable; GradientDrawableCompat(int c){drawable=new android.graphics.drawable.GradientDrawable();drawable.setColor(c);drawable.setCornerRadius(28);drawable.setStroke(1,Color.rgb(45,52,70));} }
}
