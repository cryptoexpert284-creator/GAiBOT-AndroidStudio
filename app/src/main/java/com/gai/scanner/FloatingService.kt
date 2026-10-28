package com.gai.scanner

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private var resultView: TextView? = null
    private var scanLine: TextView? = null
    private var marketView: TextView? = null
    private var priceView: TextView? = null
    private var symbol = "BNBUSDT"
    private var price = ""
    private var scanning = false
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var priceLoop: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        showPanel()
        registerMarketReceiver()
        startPriceLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun showPanel() {
        if (!Settings.canDrawOverlays(this) || panel != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.rgb(21, 25, 34))
        }

        val handle = TextView(this).apply {
            text = "━━  GAi BOT  ━━"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(141,255,22))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(2, 2, 2, 8)
        }

        marketView = TextView(this).apply {
            text = "🟡  BNB / USDT"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(0, 2, 0, 2)
        }

        priceView = TextView(this).apply {
            text = "Price: loading…   TF: 1m"
            gravity = Gravity.CENTER
            textSize = 11f
            setTextColor(0xFFB8C0D4.toInt())
            setPadding(0, 2, 0, 5)
        }

        scanLine = TextView(this).apply {
            text = "━━━━━━━━━━━━"
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.rgb(141,255,22))
            setPadding(0, 8, 0, 8)
        }

        resultView = TextView(this).apply {
            text = "BINANCE READY"
            gravity = Gravity.CENTER
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 4, 0, 8)
        }

        val scan = TextView(this).apply {
            text = "▶ SCAN BINANCE"
            gravity = Gravity.CENTER
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(141,255,22))
            setPadding(10, 10, 10, 10)
            setOnClickListener { startBinanceScan() }
        }

        root.addView(handle)
        root.addView(marketView)
        root.addView(priceView)
        root.addView(scanLine)
        root.addView(resultView)
        root.addView(scan)

        @Suppress("DEPRECATION")
        val type = if (android.os.Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(
            190, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 160
        }

        var sx=0f; var sy=0f; var px=0; var py=0
        handle.setOnTouchListener { _, e ->
            when(e.action) {
                MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; px=p.x; py=p.y; true }
                MotionEvent.ACTION_MOVE -> {
                    p.x=(px+(sx-e.rawX)).toInt().coerceAtLeast(0)
                    p.y=(py+(e.rawY-sy)).toInt().coerceAtLeast(0)
                    wm.updateViewLayout(root,p); true
                }
                else -> false
            }
        }

        wm.addView(root, p)
        panel = root
    }

    private fun registerMarketReceiver() {
        val filter = android.content.IntentFilter("com.gai.scanner.BINANCE_APP")
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
    }

    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "com.gai.scanner.BINANCE_APP") return
            val detected = intent.getStringExtra("symbol")?.uppercase(Locale.US) ?: return
            if (detected.matches(Regex("[A-Z0-9]{4,30}"))) {
                symbol = detected
                updateMarket()
                fetchPrice()
            }
        }
    }

    private fun startPriceLoop() {
        priceLoop = object : Runnable {
            override fun run() {
                fetchPrice()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(priceLoop!!)
    }

    private fun fetchPrice() {
        val target = symbol
        executor.execute {
            try {
                val url = URL("https://api.binance.com/api/v3/ticker/price?symbol=$target")
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 4000
                c.readTimeout = 4000
                c.requestMethod = "GET"
                val body = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val value = org.json.JSONObject(body).optString("price", "")
                handler.post {
                    price = value
                    priceView?.text = "Price: ${formatPrice(value)}   TF: 1m"
                    marketView?.text = "🟡  ${formatPair(symbol)}"
                }
            } catch (_: Exception) {
                handler.post { priceView?.text = "Price: unavailable   TF: 1m" }
            }
        }
    }

    private fun startBinanceScan() {
        if (scanning) return
        scanning = true
        resultView?.text = "SCANNING $symbol"
        scanLine?.setTextColor(Color.rgb(141,255,22))
        executor.execute {
            try {
                // 1-minute Binance candles; no screen capture/share permission is used.
                val url = URL("https://api.binance.com/api/v3/klines?symbol=$symbol&interval=1m&limit=25")
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.requestMethod = "GET"
                val body = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val arr = JSONArray(body)
                if (arr.length() < 5) throw Exception("Not enough candles")

                // Use the latest closed candle and recent trend as a simple public-data signal.
                val last = arr.getJSONArray(arr.length()-2)
                val prev = arr.getJSONArray(arr.length()-3)
                val open = last.getDouble(1)
                val close = last.getDouble(4)
                val prevClose = prev.getDouble(4)
                val high = last.getDouble(2)
                val low = last.getDouble(3)

                val direction = when {
                    close > open && close >= prevClose -> "UP"
                    close < open && close <= prevClose -> "DOWN"
                    else -> "NO CLEAR CANDLE"
                }

                handler.post {
                    resultView?.text = direction
                    resultView?.setTextColor(
                        when(direction) {
                            "UP" -> Color.GREEN
                            "DOWN" -> Color.RED
                            else -> Color.WHITE
                        }
                    )
                    scanLine?.setTextColor(
                        when(direction) {
                            "UP" -> Color.GREEN
                            "DOWN" -> Color.RED
                            else -> Color.rgb(141,255,22)
                        }
                    )
                    priceView?.text = "Price: ${formatPrice(price)}   TF: 1m"
                    scanning = false
                }
            } catch (e: Exception) {
                handler.post {
                    resultView?.text = "BINANCE DATA ERROR"
                    resultView?.setTextColor(Color.WHITE)
                    scanning = false
                }
            }
        }
    }

    private fun updateMarket() {
        marketView?.text = "🟡  ${formatPair(symbol)}"
    }

    private fun formatPair(s: String): String {
        val q = listOf("USDT","USDC","BUSD","FDUSD","BTC","ETH","BNB")
        for (quote in q) if (s.endsWith(quote) && s.length > quote.length)
            return "${s.removeSuffix(quote)} / $quote"
        return s
    }

    private fun formatPrice(v: String): String {
        val n = v.toDoubleOrNull() ?: return "—"
        return if (n >= 1000) String.format(Locale.US, "%,.2f", n)
        else if (n >= 1) String.format(Locale.US, "%.4f", n)
        else String.format(Locale.US, "%.8f", n)
    }

    override fun onDestroy() {
        priceLoop?.let { handler.removeCallbacks(it) }
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        executor.shutdownNow()
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        super.onDestroy()
    }
}
