# Stage 10 — EMA Crossover dan Market Alert

Pemantauan berjalan per timeframe M1, M2, M3, M5, M15, M30, H1, H4, H8, H12, D1, W1. Hanya snapshot LIVE dan candle XAUUSD tertutup tervalidasi yang diproses.

EMA20/EMA50 memakai SMA seed dan alpha 2/(period+1), sama dengan IndicatorEngine. Bullish cross: EMA20 candle sebelumnya <= EMA50 lalu EMA20 terbaru > EMA50. Bearish cross kebalikannya. Alignment bullish/bearish mensyaratkan urutan EMA dan close berada di sisi EMA20 yang searah.

Retest sukses: candle menyentuh EMA20 (prioritas) atau EMA50, close kembali searah alignment, dan wick rejection minimal 0.8x body. Retest gagal: close menembus sisi berlawanan EMA yang disentuh. Crossover gagal: urutan EMA berbalik dalam maksimal tiga candle tertutup setelah cross; ini aturan operasional awal, bukan edge statistik teruji.

Break Support/Resistance memakai close yang menembus swing S/R terdekat dari candle sebelumnya dengan toleransi max(0.05 ATR14, 0.0001% harga). Supply/Demand breakout, false breakout, BOS dan CHOCH hanya meneruskan event tervalidasi dari engine tahap 6. Reversal hanya dari pola ENTRY_CONFIRMED. BUY/SELL hanya dari status BUY/SELL SignalScoringEngine setelah seluruh hard gate tahap 7 lolos.

Payload alert memuat nama event, timeframe, harga, timestamp candle, status, alasan, dan invalidasi. Deduplikasi per event+timeframe+candle; cooldown 5 menit. Alert tidak diulang pada pemrosesan snapshot yang sama.

Tahap ini menampilkan event di panel aplikasi. Android notification channel dan suara belum diaktifkan pada tahap ini. Alert bukan klaim win rate. Sumber OHLC live tetap bergantung validasi endpoint/provider BiQuote dan data timeframe yang tersedia.
