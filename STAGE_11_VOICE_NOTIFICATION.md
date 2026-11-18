# Tahap 11 — Voice Engine & Notification Center
- TextToSpeech Android dengan antrean; ToneGenerator membunyikan bell, lalu TTS dimulai 480 ms kemudian.
- Prioritas suara perempuan Bahasa Indonesia berdasarkan metadata/nama voice yang tersedia. Android tidak menyediakan metadata gender konsisten; pemilihan perempuan bersifat best-effort. Jika tidak ditemukan, gunakan suara Indonesia tersedia dan tampilkan status fallback.
- Daftar voice perangkat, TEST VOICE, pengaturan suara aktif/nonaktif.
- Notification Channel Android, izin POST_NOTIFICATIONS runtime pada Android 13+, dan notifikasi tanpa bunyi sistem agar urutan bell → ucapan tidak tertimpa.
- Riwayat SQLite: waktu, judul, pesan, status dibaca/belum dibaca.
- Preferensi lokal: notifikasi, suara, jenis alert, cooldown, quiet hours. Quiet hours menahan suara; notifikasi/riwayat tetap tersedia.
- Data stale/invalid tidak boleh disuarakan. BUY/SELL hanya diucapkan jika status CONFIRMED.
- Jenis: EMA, BOS, CHOCH, breakout, false breakout, retest, rejection, Supply touched, Demand touched, BUY/SELL confirmed, NEWS_ALERT.
- NEWS_ALERT memiliki jalur publish di NotificationCenter, tetapi belum tersambung penyedia berita eksternal pada tahap ini.
## Pengujian
Unit test memeriksa stale gate, type enable, cooldown, quiet hours, dan larangan BUY/SELL sebelum confirmed. GitHub Actions memeriksa compile, test, dan assembleDebug. Suara speaker, pemilihan suara perempuan aktual, dialog izin dan tampilan notifikasi tetap memerlukan uji perangkat fisik; CI tidak mempunyai speaker/UI izin.