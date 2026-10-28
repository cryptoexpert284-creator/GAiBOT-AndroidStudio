# GAi BOT Android

Android conversion of the supplied GAi BOT Binance extension. The original extension files are kept under `legacy-extension/` unchanged. The Android app runs the popup-style UI locally inside an embedded Android WebView, so the user does not need to open Binance in a browser.

## Included
- Mobile full-screen popup-style UI
- Binance USDT spot market list with search
- Coin logos and live price refresh
- Selected coin live kline stream
- 10-second scan countdown
- LONG/SHORT signal with TP1, TP2 and SL
- The same eight strategy categories from the supplied scanner: Market Structure, Liquidity Sweep, Price Action, Volume Confirmation, Support/Resistance, Order Block, Fair Value Gap, Breakout + Retest
- Original browser extension preserved in `legacy-extension/`
- GitHub Actions workflow at `.github/workflows/build-apk.yml`

## Build on GitHub
1. Create a GitHub repository.
2. Upload all files from this project.
3. Open **Actions** → **Build GAi BOT Android APK** → **Run workflow**.
4. Download the `GAi-BOT-Android-debug-APK` artifact.

The workflow uses GitHub Actions, Android SDK and Gradle. GitHub workflows live in `.github/workflows/`.

## Important
Signals are informational only. TP/SL are ATR-based planning levels and are not guaranteed. The app does not place Binance orders.
