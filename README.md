# HordesPaper (Paper 26.3, Java 25, Server-Only)

Porting server-only mod *The Hordes* (NeoForge `1.21.1-1.6.3f`, lisensi LGPL-2.1) ke **Paper 26.3** (`io.papermc.paper:paper-api:26.3.build.255-stable`, Java 25) dengan revisi PRD v3: **R1** (zombie biasa dewasa saja), **R2** (25 zombie tiap 10 detik, batch 5 per 4 tick, cap 200/pemain & 400 global), dan **R3** (zombie gila agresif dengan `speed-multiplier: 1.6`, `follow-range: 100.0`, `break-doors`, `no-sunburn`, `target-lock`).

## Cara Mendapatkan JAR via GitHub Actions (Tanpa Instal JDK/Gradle)

1. Buat repositori baru di GitHub lalu unggah seluruh isi folder proyek ini (termasuk folder `.github/workflows/build.yml`).
2. Buka tab **Actions** → pilih workflow **Build & Verify HordesPaper** → tunggu selesai (otomatis jalan saat push ke `main`/`master` atau klik **Run workflow**).
3. Di bagian **Artifacts**, unduh:
   - `HordesPaper-jar-VERIFIED` (atau `HordesPaper-jar-VERIFIED-PARTIAL`) — berisi file `HordesPaper-*.jar` siap pakai untuk folder `plugins/` server Paper 26.3.
   - `neon-report` — hasil detail tes penerimaan **AT-01 s.d. AT-23**.
   - `build-log` — log lengkap kompilasi Gradle 9.1.0 (Java 25).
4. Salin `HordesPaper-*.jar` ke folder `plugins/` server Paper 26.3 milikmu, lalu restart server.

## Cara Menjalankan Uji Lokal (Opsional)

1. Pastikan **JDK 25**, **Gradle 9.1.0+**, `curl`, dan `jq` (atau Python 3) tersedia.
2. Jalankan `./run_all.sh` (Linux/macOS) atau `.\run_all.ps1` (Windows PowerShell).
3. Skrip membangun plugin, mengunduh Paper 26.3 STABLE terbaru, menyetujui EULA hanya di `test-server/`, menjalankan **AT-01 s.d. AT-23**, dan menyimpan laporan ke `neon-report.txt` serta `build.log`.

## Checklist Verifikasi Manual (Jika CI Berlabel `VERIFIED-PARTIAL`)

Pada server uji headless tanpa pemain online, Paper 26.3 terkadang tidak menjalankan full entity ticking meski chunk sudah di-force-load; bila itu terjadi, `AT-18` dan `AT-19` otomatis ditandai `[INFO] ... SKIPPED` dan build berlabel `VERIFIED-PARTIAL`. Lakukan 2 uji singkat ini saat masuk ke server Paper 26.3 sebagai pemain Survival:

- **Checklist AT-18 (No Sunburn):**
  1. Jalankan `/time set noon` dan `/weather clear` di area terbuka tanpa atap.
  2. Jalankan `/hordes spawnWave @s 5`.
  3. Amati 5 zombie horde selama minimal 10 detik (200 tick): pastikan tidak ada yang terbakar api matahari.
- **Checklist AT-19 (Kejar 60 Blok <= 30 Detik):**
  1. Berdiri di tanah datar terbuka (`simulation-distance` minimal `10` di `server.properties`).
  2. Jalankan `/hordes start @s 6000` (atau `/hordes spawnWave @s 5`).
  3. Ukur jarak awal zombie (~60–75 blok) dan pastikan zombie berlari cepat mengejar hingga jarak <= 3 blok dalam waktu <= 30 detik tanpa macet atau berhenti.
