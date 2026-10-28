# GAi BOT Android — Binance app connection

This revision connects the floating GAi BOT panel to Binance public market data and the currently selected Binance app pair.

## Changes
- Detects the selected Binance pair (for example BNB/USDT) through Android Accessibility.
- Gets the live price directly from Binance public REST API.
- SCAN BINANCE no longer requests Android screen sharing.
- Scan reads Binance 1-minute candles directly from the public API.
- The floating panel updates when the selected Binance pair changes.
- No Binance API key and no trading/order permission are required.

## One-time setup
1. Install the APK.
2. Allow "Display over other apps" for GAi BOT.
3. In GAi BOT tap **Connect app**.
4. Android Accessibility settings will open. Enable GAi BOT.
5. Open the Binance Android app and open the coin chart you want.
6. The floating panel should change to that pair and show its live Binance price.
7. Tap **SCAN BINANCE** to scan Binance candles.

## Important
The scanner is a market-data heuristic, not a guarantee of winning trades. It does not place orders.
