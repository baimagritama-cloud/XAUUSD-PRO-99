# Stage 9 — Entry Risk and Market Journey

## EntryRiskEngine
- Tidak menerbitkan entry kecuali hasil Stage 7 sendiri berstatus BUY/SELL dan skor >=57. WAIT/NO_TRADE tidak pernah dinaikkan.
- Memerlukan pola arah sesuai pada M1/M2/M3/M5 dengan ENTRY_CONFIRMED, lokasi Demand/Support atau Supply/Resistance valid, reaksi candle, trend utama atau CHOCH reversal terkonfirmasi, struktur utama + timing, dan indikator/DI/momentum mendukung.
- Entry memakai Ask untuk BUY dan Bid untuk SELL.
- SL memakai extreme pola, batas invalidasi zona bila pola terbentuk di dalam zona, serta swing terkonfirmasi yang relevan. Buffer dinamis = max(1.5 × spread, 0.15 × ATR14). SL diblokir jika jarak melebihi max(2.5 × ATR14 entry timeframe, 3 × spread).
- TP hanya berasal dari active opposing Supply/Demand, swing S/R indikator, dan confirmed swing market structure. TP harus RR >=1.5; TP2/TP3 hanya tampil bila ada level independen tambahan. Tidak ada target ATR sintetis.
- Data/level hilang menghasilkan BLOCKED/N/A.

## MarketJourneyEngine
- Menampilkan quote aktual, arah utama W1/D1/H4, zona Supply/Demand aktif, swing terdekat, tujuan, state BREAKOUT/RETEST/REJECTION/CONSOLIDATION/UNKNOWN, skenario bullish/bearish bersyarat, invalidasi, reversal dan area SAFE/CAUTION/UNKNOWN.
- Safe area hanya jarak relatif terhadap level struktural terdekat: max(0.5 × ATR14 M5, 2 × spread); bukan jaminan aman.
- Skenario memakai close M5 dan retest. Wick saja bukan breakout valid.
- Semua harga bersumber dari quote, OHLC, active zone, atau confirmed swing.

## Probability
Tidak ada probabilitas yang dibuat dari skor. Skor hanya kualitas 0–100. Probabilitas memerlukan backtest kronologis memadai dan validasi out-of-sample yang belum tersedia.

## Limitations
BiQuote OHLC dan ketersediaan H8/H12/W1 belum diverifikasi dengan payload live yang tertangkap. Hard gate 12 timeframe Stage 7 tidak diubah.