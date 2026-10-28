package com.gai.scanner

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

class MainActivity : Activity() {
    private val captureRequest = 701
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var lastSymbol = ""
    private var lastTimeframe = "--"
    private var lastPriceFetch = 0L
    private var watcherStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF12141C.toInt()) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 8, 14, 8); setBackgroundColor(0xFF151922.toInt()) }
        bar.addView(TextView(this).apply { text = "GAi BOT  ·  Binance Bahrain"; textSize = 18f; setTextColor(0xFF8DFF16.toInt()) })
        status = TextView(this).apply { text = "Opening Binance…"; textSize = 12f; setTextColor(0xFFB8C0D4.toInt()); setPadding(0, 4, 0, 6) }
        bar.addView(status)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(Button(this).apply { text = "Open Binance"; setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.binance.com/en/trade/BNB_USDT"))) } catch (_: Exception) {} } }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(Button(this).apply { text = "Connect app"; setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(Button(this).apply { text = "Start popup"; setOnClickListener { ensureOverlayAndStart() } }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(controls)
        root.addView(bar)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    status.text = "Binance connected · open any coin chart"
                    startPageWatcher()
                }
            }
            addJavascriptInterface(PageBridge(), "GaiBridge")
        }
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (savedInstanceState == null) webView.loadUrl("https://www.binance.bh/en/markets/overview")
        
        ensureOverlayAndStart()
    }

    private fun ensureOverlayAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            startService(Intent(this, FloatingService::class.java))
        }
    }

    private fun requestCapture() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), captureRequest)
    }

    private fun startPageWatcher() {
        if (watcherStarted) return
        watcherStarted = true
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isFinishing || !::webView.isInitialized) return
                webView.evaluateJavascript("(function(){var u=location.href;var t=document.title||'';var tf='';var m=u.match(/[?&](?:interval|timeframe)=([^&]+)/i);if(m)tf=decodeURIComponent(m[1]);if(!tf){var a=Array.from(document.querySelectorAll('button,[role=button]'));var opts=['1m','3m','5m','15m','30m','1h','2h','4h','6h','8h','12h','1d','3d','1w','1M'];for(var e of a){var s=(e.innerText||'').trim();if(opts.includes(s)&&(e.getAttribute('aria-pressed')==='true'||e.getAttribute('data-state')==='active'||e.getAttribute('class')?.includes('active'))){tf=s;break;}}}GaiBridge.onPage(u,t,tf);})()", null)
                handler.postDelayed(this, 1800)
            }
        }, 1200)
    }

    inner class PageBridge {
        @JavascriptInterface fun onPage(url: String, title: String, timeframe: String) {
            val symbol = parseSymbol(url, title)
            val tf = timeframe.ifBlank { parseTimeframe(url) }.ifBlank { "--" }
            runOnUiThread { status.text = if (symbol.isNotBlank()) "Detected: $symbol · $tf" else "Binance connected · open a coin chart" }
            if (symbol.isNotBlank()) {
                val changed = symbol != lastSymbol || tf != lastTimeframe
                lastSymbol = symbol; lastTimeframe = tf
                sendMarket(symbol, "", tf, symbol.substringBefore("USDT").ifBlank { symbol })
                if (changed || System.currentTimeMillis() - lastPriceFetch > 2500) fetchPrice(symbol, tf)
            }
        }
    }

    private fun parseSymbol(url: String, title: String): String {
        val path = try { Uri.parse(url).path ?: "" } catch (_: Exception) { "" }
        val trade = Regex("/trade/([A-Za-z0-9]+)_([A-Za-z0-9]+)").find(path)
        if (trade != null) return (trade.groupValues[1] + trade.groupValues[2]).uppercase()
        val fromTitle = Regex("([A-Z0-9]{2,20})\\s*[/_-]\\s*(USDT|USDC|BTC|ETH|BNB)", RegexOption.IGNORE_CASE).find(title)
        if (fromTitle != null) return (fromTitle.groupValues[1] + fromTitle.groupValues[2]).uppercase()
        return ""
    }

    private fun parseTimeframe(url: String): String = try {
        val u = Uri.parse(url)
        (u.getQueryParameter("interval") ?: u.getQueryParameter("timeframe") ?: "").uppercase()
    } catch (_: Exception) { "" }

    private fun fetchPrice(symbol: String, tf: String) {
        lastPriceFetch = System.currentTimeMillis()
        executor.execute {
            var price = ""
            try {
                val connection = (URL("https://api.binance.com/api/v3/ticker/price?symbol=$symbol").openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 5000; requestMethod = "GET" }
                try { if (connection.responseCode == 200) price = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optString("price") } finally { connection.disconnect() }
            } catch (_: Exception) { }
            sendMarket(symbol, price, tf, symbol.removeSuffix("USDT").removeSuffix("USDC"))
        }
    }

    private fun sendMarket(symbol: String, price: String, timeframe: String, base: String) {
        sendBroadcast(Intent("com.gai.scanner.MARKET").setPackage(packageName)
            .putExtra("symbol", symbol).putExtra("price", price).putExtra("timeframe", timeframe).putExtra("base", base))
    }

    @Deprecated("Deprecated by platform but retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, ScreenCaptureService::class.java).putExtra("resultCode", resultCode).putExtra("resultData", data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        }
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); executor.shutdownNow(); if (::webView.isInitialized) webView.destroy(); super.onDestroy() }
}
