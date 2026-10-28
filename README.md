# GAi BOT Android — Floating Persistent Signal

This version keeps the GAi BOT floating panel alive when the main app screen is closed or swiped away from Recents. The foreground service continues running and a hidden WebView engine continues the Binance price/candle scanner.

Features:
- Small floating panel outside the app
- Live Binance price
- 10-second scan
- LONG/SHORT signal
- TP1 / TP2 / SL
- Existing 8-strategy JavaScript scanner retained
- Close `×` on the floating panel stops the service and removes the popup
- Boot/package-update receiver restarts the foreground service

Important Android behavior:
- The user must allow **Display over other apps**.
- The app may need **battery/background activity unrestricted** on some Samsung/Xiaomi/Oppo/etc. devices.
- Android's **Force stop** always stops the app/service; no normal Android app can bypass a user Force Stop.
- Swiping the app away from Recents is handled by the persistent foreground service.
