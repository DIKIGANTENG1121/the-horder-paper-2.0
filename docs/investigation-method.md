# Metodologi Investigasi (Adaptasi REA) — Porting The Hordes ke Paper 26.3

Dokumen ini merangkum metodologi investigasi berbasis bukti yang diadaptasi dari proyek resmi [REA (Reverse Engineer Anything)](https://github.com/morluto/rea) ([README](https://github.com/morluto/rea/blob/main/README.md), [Supported Targets](https://github.com/morluto/rea/blob/main/docs/reference/supported-targets.md), dan [SKILL.md](https://github.com/morluto/rea/blob/main/.agents/skills/reverse-engineer-anything/SKILL.md)), disesuaikan untuk analisis statis mod Minecraft NeoForge (JAR JVM) ke plugin Paper 26.3.

## 1. Status REA dan Evaluasi Kesesuaian Target

- **Status MCP/CLI di Lingkungan Eksekusi:** `rea-agents` tidak terpasang secara lokal; Node.js yang tersedia adalah `v18.20.4` (di bawah syarat resmi REA yaitu `Node.js 22.x >=22.23.4`, `24.x >=24.22.1`, atau `26.12.1+` — [Supported Targets](https://github.com/morluto/rea/blob/main/docs/reference/supported-targets.md)), dan akses jaringan keluar pada sandbox VM dinonaktifkan sehingga `npx -y rea-agents@latest doctor` tidak dapat dijalankan tanpa izin/perubahan lingkungan.
- **Kesesuaian Target REA terhadap JAR Java / Mod Minecraft:** Berdasarkan [Supported Targets](https://github.com/morluto/rea/blob/main/docs/reference/supported-targets.md) dan routing di [SKILL.md](https://github.com/morluto/rea/blob/main/.agents/skills/reverse-engineer-anything/SKILL.md), REA **tidak memiliki target khusus untuk JAR Java atau mod Minecraft**. Target terdekat di REA adalah inventaris arsip umum (`open_binary` / `inspect_artifact` untuk ZIP) dan Android APK (`inspect_android_package` via JADX + full JDK). Selain itu, `SKILL.md` REA secara eksplisit menyatakan: *"For ordinary analysis of a complete source repository, use normal repository tools and do not run REA readiness or provider commands."*
- **Pilihan Alat Analisis Statis untuk Proyek Ini:**
  - **Sumber utama:** Source code resmi [SmileycorpMC/The-Hordes](https://github.com/SmileycorpMC/The-Hordes) (lisensi LGPL-2.1) dan data bawaan di dalam artefak rilis `The-Hordes-1.21.1-1.6.3f.jar` (SHA-256 `4f4e308d4a412ffd9a2eee71bfb7cf729c306524ccac6b61ab4dcbca648a8574`).
  - **Verifikasi silang JAR (tanpa eksekusi):** Karena `javap`, `jadx`, `cfr`, dan `vineflower` tidak terpasang di sandbox VM (hanya OpenJDK 17 JRE), analisis statis artefak JAR dilakukan menggunakan parser binary Python (`zipfile` + pembaca struktur JVM ClassFile / constant pool / bytecode opcodes) terhadap file `.class`, `META-INF/neoforge.mods.toml`, `META-INF/accesstransformer.cfg`, `hordes.mixin.json`, dan seluruh JSON di `config_defaults/` serta `data/`.

## 2. Siklus Investigasi Berbasis Bukti (5 Langkah)

1. **Rumuskan Pertanyaan Perilaku Spesifik:** Pecah setiap subsistem mod (penjadwal horde, spawn table, infeksi 4 fase, zombie player, perintah `/hordes`, perubahan AI vanilla/mixin, ketergantungan AtlasLib) menjadi pertanyaan terukur (mis. rumus peluang infeksi, satuan durasi imunitas, nilai default config, urutan prioritas AI).
2. **Kumpulkan Bukti Statis Terverifikasi:** Baca file konfigurasi bawaan, metadata, daftar kelas, constant pool, field default, dan urutan instruksi bytecode dari JAR (plus verifikasi silang dengan source GitHub). Catat identitas artefak (nama file, ukuran, SHA-256) dan lokasi bukti (nama kelas/file dan konstanta/baris).
3. **Pisahkan Bukti, Inferensi, dan Batasan:**
   - **Terverifikasi:** Terbaca langsung dari file data JSON, metadata, source, atau bytecode `.class` pada JAR referensi.
   - **Disimpulkan:** Diturunkan secara logis dari struktur kelas/signature/constant pool ketika dekompilasi penuh tidak tersedia, disertai catatan batasannya.
   - **Tidak diketahui:** Tidak terbaca dari artefak maupun dokumentasi; dilarang menebak.
4. **Identifikasi Batas Platform (Client vs Server-Only):** Pisahkan seluruh logika client-side (`MixinLevelRenderer`, `MixinLivingEntityRenderer`, `MixinVillagerProfessionLayer`, shader `infected_stage_4.json`, layer tekstur custom) dari logika server-side. Jangan memalsukan fitur client; tandai sebagai *Parsial* atau *Tidak mungkin* secara transparan.
5. **Verifikasi API Target & Uji Penerimaan:** Setiap API Paper 26.3 yang dipilih diverifikasi langsung terhadap [Javadoc resmi Paper 26.3](https://jd.papermc.io/paper) (Java 25). Fitur hanya dinyatakan selesai secara runtime setelah skenario uji penerimaan (AT-01 s.d. AT-14) dijalankan dan lulus; dalam Mode B, status wajib ditandai *belum terverifikasi runtime*.

## 3. Aturan Keamanan & Batas Ruang Lingkup

- **Analisis murni statis:** JAR mod NeoForge **tidak pernah dijalankan** pada JVM.
- **Replikasi kode bersih:** Perubahan dilakukan dengan menulis kode plugin Paper 26.3 baru yang kompatibel dengan LGPL-2.1, bukan memodifikasi atau mem-patch JAR mod asli.
- **Keterlacakan bukti:** Setiap keputusan desain di `parity-matrix.md` dan `CONFIG_MAPPING.md` wajib merujuk ke file/kelas bukti di JAR/source serta Javadoc Paper 26.3 yang relevan.
