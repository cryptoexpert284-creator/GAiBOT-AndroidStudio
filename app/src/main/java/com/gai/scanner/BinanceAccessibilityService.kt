package com.gai.scanner

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import java.util.Locale

class BinanceAccessibilityService : AccessibilityService() {

    private var lastSymbol = ""
    private var lastSentAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()?.lowercase(Locale.US) ?: return
        if (!pkg.contains("binance")) return

        val texts = ArrayList<String>()
        collect(event.source, texts)
        event.text?.forEach { texts.add(it.toString()) }

        val symbol = findSymbol(texts)
        if (symbol.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (symbol != lastSymbol || now - lastSentAt > 1500) {
                lastSymbol = symbol
                lastSentAt = now
                sendBroadcast(Intent("com.gai.scanner.BINANCE_APP").setPackage(packageName)
                    .putExtra("symbol", symbol)
                    .putExtra("package", pkg))
            }
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) collect(node.getChild(i), out)
    }

    private fun findSymbol(texts: List<String>): String {
        val joined = texts.joinToString(" ")
        val candidates = listOf(joined) + texts
        val patterns = listOf(
            Regex("""\b([A-Z0-9]{2,20})\s*/\s*(USDT|USDC|BUSD|FDUSD|BTC|ETH|BNB)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b([A-Z0-9]{2,20})[_\-\s](USDT|USDC|BUSD|FDUSD|BTC|ETH|BNB)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b([A-Z0-9]{2,20})(USDT|USDC|BUSD|FDUSD)\b""", RegexOption.IGNORE_CASE)
        )
        for (text in candidates) {
            for (rx in patterns) {
                val m = rx.find(text) ?: continue
                val base = m.groupValues[1].uppercase(Locale.US)
                val quote = m.groupValues[2].uppercase(Locale.US)
                if (base.length >= 2 && base != quote) return base + quote
            }
        }
        return ""
    }

    override fun onInterrupt() {}
}
