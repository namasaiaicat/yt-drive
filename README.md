# YT Drive

Aplikasi Android pribadi: download video YouTube jadi MP4, tersimpan otomatis ke folder Google Drive. Satu layar, tanpa database, tanpa backend, tanpa login.

## Download APK

https://github.com/namasaiaicat/yt-drive/releases/download/latest/yt-drive.apk

APK debug-signed, langsung bisa di-install (ARM64).

## Cara pakai

1. Buka app, pilih folder tujuan (Google Drive) sekali saja. Ganti kapan saja lewat tombol kecil.
2. Tempel link YouTube (atau Share dari aplikasi YouTube langsung ke YT Drive, atau copy link lalu buka app — terisi otomatis).
3. Tekan **Generate MP4**. Progress tampil di layar dan notifikasi. File tersimpan ke folder tujuan, cache dibersihkan otomatis.

## Catatan

- yt-dlp diperbarui otomatis tiap app dibuka; ada tombol manual "Update yt-dlp".
- Unduhan jalan di Foreground Service (dataSync) agar tidak dimatikan sistem.
- Penulisan ke Google Drive memakai Storage Access Framework; kalau provider Drive menolak, app menampilkan pesan yang jelas.

## Build

APK dibangun otomatis oleh GitHub Actions (`assembleDebug`) dan dirilis ke tag `latest` setiap push ke `main`.
