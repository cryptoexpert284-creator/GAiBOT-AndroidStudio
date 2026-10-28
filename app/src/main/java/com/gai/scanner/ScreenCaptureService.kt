package com.gai.scanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var frames = 0
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startNotice()
        val code = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("resultData", Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra("resultData")
        if (data == null || code == 0) { stopSelf(); return START_NOT_STICKY }
        try {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(code, data)
            val metrics = DisplayMetrics(); @Suppress("DEPRECATION") (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val w = (metrics.widthPixels / 3).coerceAtLeast(240); val h = (metrics.heightPixels / 3).coerceAtLeast(400)
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            thread = HandlerThread("gai-screen-capture").also { it.start() }
            reader?.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try { frames++; if (frames % 4 == 0) { val signal = classify(image); sendBroadcast(Intent("com.gai.scanner.RESULT").setPackage(packageName).putExtra("signal", signal)) } } finally { image.close() }
            }, Handler(thread!!.looper))
            projection?.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, Handler(thread!!.looper))
            display = projection?.createVirtualDisplay("GAiBotScreen", w, h, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, Handler(thread!!.looper))
        } catch (_: Exception) { sendBroadcast(Intent("com.gai.scanner.RESULT").setPackage(packageName).putExtra("signal", "CAPTURE ERROR")); stopSelf() }
        return START_NOT_STICKY
    }
    private fun startNotice() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("gai_scan", "GAi screen scan", NotificationManager.IMPORTANCE_LOW))
        val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "gai_scan") else @Suppress("DEPRECATION") Notification.Builder(this)
        n.setContentTitle("GAi BOT screen scan active").setContentText("Tap the Android screen-sharing indicator to stop capture").setSmallIcon(android.R.drawable.ic_menu_view)
        startForeground(42, n.build())
    }
    // Basic visual heuristic: counts saturated green/red pixels in the captured screen.
    // It does not understand chart layout or replicate the extension's eight-strategy engine.
    private fun classify(image: Image): String {
        val plane = image.planes[0]; val buf = plane.buffer; val pixelStride = plane.pixelStride; val rowStride = plane.rowStride
        val bytes = ByteArray(buf.remaining()); buf.get(bytes)
        var green=0; var red=0; var seen=0
        val stepX = (image.width / 100).coerceAtLeast(1); val stepY = (image.height / 160).coerceAtLeast(1)
        for (y in 0 until image.height step stepY) for (x in 0 until image.width step stepX) {
            val idx = y*rowStride + x*pixelStride
            if (idx+2 >= bytes.size) continue
            val r=bytes[idx].toInt() and 255; val g=bytes[idx+1].toInt() and 255; val b=bytes[idx+2].toInt() and 255
            if (g > 125 && g > r*1.25 && g > b*1.12) green++
            if (r > 135 && r > g*1.25 && r > b*1.15) red++
            seen++
        }
        if (seen == 0 || kotlin.math.max(green, red) < 5 || kotlin.math.abs(green-red) < 3) return "NO CLEAR CANDLE"
        return if (green > red*1.12) "UP" else if (red > green*1.12) "DOWN" else "NO CLEAR CANDLE"
    }
    override fun onDestroy() { display?.release(); reader?.close(); projection?.stop(); thread?.quitSafely(); super.onDestroy() }
}
