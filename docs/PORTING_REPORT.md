# Porting Report — The Hordes (NeoForge 1.21.1-1.6.3f) → HordesPaper (Paper 26.3)

**Tanggal:** 22 November 2026
**Mode Eksekusi:** **Mode B** (lingkungan sandbox tidak memiliki JDK 25, Gradle, maupun akses jaringan keluar)
**Status Kompilasi & Runtime:** **BELUM TERVERIFIKASI** — kompilasi Java 25 dan eksekusi Paper 26.3 dijalankan melalui GitHub Actions (`.github/workflows/build.yml`) atau skrip lokal `run_all.sh` / `run_all.ps1`. Label `VERIFIED` / `VERIFIED-PARTIAL` hanya diberikan oleh gate CI berdasarkan `test-server/neon-testkit-results.txt`.

## 1. Identitas Artefak & Target

- **Mod Referensi:** `The-Hordes-1.21.1-1.6.3f.jar` (449.531 byte, SHA-256 `4f4e308d4a412ffd9a2eee71bfb7cf729c306524ccac6b61ab4dcbca648a8574`), NeoForge 1.21.1, author `smileycorp`, sumber [SmileycorpMC/The-Hordes](https://github.com/SmileycorpMC/The-Hordes) / [Modrinth](https://modrinth.com/mod/the-hordes/version/1.21.1-1.6.3f).
- **Target Platform:** Paper `26.3`, Java 25, server-only. Versi API dipin tetap (tanpa `+`): `io.papermc.paper:paper-api:26.3.build.255-stable` di `build.gradle.kts` dan `neon-testkit/build.gradle.kts` (bukti: header Javadoc resmi PaperMC 26.3 pada [ItemStack](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/ItemStack.html), [Player](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html), [Chunk](https://jd.papermc.io/paper/26.3/org/bukkit/Chunk.html), dan [allclasses-index](https://jd.papermc.io/paper/26.3/allclasses-index.html)).
- **Build & CI:** Tanpa `gradle-wrapper.jar`; memakai `gradle` 9.1.0+ dengan Temurin JDK 25 di `.github/workflows/build.yml`, `run_all.sh`, dan `run_all.ps1`.
- **Ketergantungan Eksternal:** Nol (AtlasLib tidak dipakai; utilitas `WeightedOutputs` dan logika spawn ditulis native).

## 2. Ringkasan Fitur & Revisi PRD v3 (R1, R2, R3)

- **Revisi R1 (Zombie Biasa Dewasa Saja):** `tables.yml` dikunci hanya `ZOMBIE`; `buildSpawnDataCore` / `startHordeCore` mengunci `tableId` ke `hordes:default`; `spawnHordeZombieAt` memakai `World#spawn(..., SpawnReason.CUSTOM, false, null)` (`randomizeData=false`) + `setAdult()` + penghapusan vehicle/passenger (`chicken jockey`) dan pengosongan equipment (`equipment: "none"`). `zombie-players.drowned-players` dan `husk-players` diset `false`.
- **Revisi R2 (Gelombang 25 Zombie / 10 Detik):** `spawn-amount: 25`, `spawn-interval-ticks: 200`, `spawn-batch-size: 5`, `spawn-batch-delay-ticks: 4`, `spawn-max: 200`, `global-zombie-cap: 400`, peringatan `simulation-distance` saat boot, logging kegagalan posisi spawn, dan penghitungan global cap sekali per gelombang/batch (`BUG-7`).
- **Revisi R3 (Zombie Gila ala The Last of Us):** Bagian `horde-zombie:` di `config.yml` (`speed-multiplier: 1.6`, `follow-range: 100.0`, `target-lock: true`, `break-doors: true`, `no-sunburn: true`, `no-baby: true`, `no-reinforcements: true`, `health-multiplier: 1.0`, `knockback-resistance: 0.2`, `equipment: "none"`, `drops-multiplier: 0.5`, `xp-multiplier: 0.5`), antrean pathfinding beranggaran maks 20 zombie/tick, serta fallback pendobrakan pintu kayu/pagar berbasis tick (`40` tick berkelanjutan, maks `4` pintu/detik, `BUG-5`).
- **Default Mod Asli Terkunci:** Durasi horde (`6000`), jam mulai (`18000`), `spawn-days: 10`, infeksi 4 fase (`6000` tick, pengurangan `0.95^n`, resistansi dasar `0.25`, cure `golden_apple`/`enchanted_golden_apple`), seluruh perintah `/hordes`, opsi tidur, dan opsi misc tetap utuh.
- **Fitur Parsial / Tidak Mungkin (Jujur):**
  - **F-04 (Zombie Player):** Gameplay penuh; skin tubuh penuh & cape/elytra diganti `PLAYER_HEAD` + nama kustom (server-only).
  - **F-06 (Atribut kustom):** Rumus identik di server; custom `Attribute` client tidak tersedia di Paper server-only.
  - **F-08 & F-15 (Misc & AI):** Murni Paper API tanpa NMS; detail mixin seperti `piglinsCureThemself` dari inventory sendiri tidak direplikasi penuh.
  - **F-10 (Visual):** BossBar, ActionBar, partikel, dan suara vanilla (tanpa resource pack).
  - **F-11 (Tekstur zombified) & X-01 (Client rendering):** Tidak mungkin tanpa resource pack / mod client.
  - **F-17 (Skrip):** Disederhanakan ke `scripts.yml` (`biome`, `day`, `random`, `not`; `set_spawn_type`, `set_spawntable` tetap dikunci ke `hordes:default` oleh R1).

## 3. Status `TODO-VERIFY`

**0 `TODO-VERIFY` tersisa.** Seluruh 4 titik API Paper 26.3 sebelumnya sudah diverifikasi di Javadoc resmi Paper 26.3:
- `Mob#setDespawnInPeacefulOverride(TriState)` — [Mob.html](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Mob.html)
- `ItemStack.serializeItemsAsBytes(Collection<ItemStack>)` & `ItemStack.deserializeItemsFromBytes(byte[])` — [ItemStack.html](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/ItemStack.html)
- Adventure `Sound.sound(Key.key(...), Sound.Source.HOSTILE, ...)` via `Audience#playSound` (menggantikan `Registry.SOUNDS`) — [Player.html](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html)

## 4. Status Tes Penerimaan (AT-01 s.d. AT-23)

Seluruh 23 tes (`AT-01` s.d. `AT-23`) diimplementasikan di `neon-testkit` (`AcceptanceTests.java`) dan dieksekusi otomatis oleh GitHub Actions / `run_all.sh` / `run_all.ps1`:

- **Tes Wajib Inti (19 ID — wajib `[PASS]` untuk `VERIFIED` maupun `VERIFIED-PARTIAL`):** `AT-01`, `AT-02`, `AT-03`, `AT-04`, `AT-05`, `AT-06`, `AT-07`, `AT-09`, `AT-10`, `AT-11`, `AT-12`, `AT-13`, `AT-14`, `AT-15`, `AT-16`, `AT-17`, `AT-20`, `AT-22`, `AT-23`.
- **Tes Wajib Bergantung Tick Entitas (2 ID):** `AT-18`, `AT-19` (menggunakan `15x15` chunk tickets + `setChunkForceLoaded` + cek `Entity#getTicksLived()` selama 20 tick; bila server headless tidak men-tick entitas tanpa pemain, status menjadi `[INFO] ... SKIPPED` dan CI menghasilkan label `VERIFIED-PARTIAL` beserta checklist manual di `README.md`).
- **Tes Beban Informasional (2 ID — `[INFO]`):** `AT-08` (100 mob, tunggu 100 tick, ukur `Bukkit.getAverageTickTime()` & `getTPS()`) dan `AT-21` (200 horde zombie, tunggu 100 tick, ukur MSPT & TPS).
- **Status Saat Ini:** **PENDING (BELUM TERVERIFIKASI RUNTIME — Mode B)** sampai workflow GitHub Actions selesai berjalan.

## 5. Kepatuhan Lisensi (LGPL-2.1)

- Mod asli **The Hordes** berlisensi **LGPL-2.1-only** milik `smileycorp`.
- Repositori ini menyertakan teks penuh `LICENSE` (GNU LGPL-2.1) dan `NOTICE` dengan atribusi ke `smileycorp` serta tautan ke [SmileycorpMC/The-Hordes](https://github.com/SmileycorpMC/The-Hordes).
