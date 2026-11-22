# Debug & Self-Healing Log — HordesPaper (Paper 26.3)

## Iterasi 0 — Mode B Pre-Compile Static Audit (22 November 2026)

- **Lingkungan Sandbox:** OpenJDK 17.0.21 JRE (tanpa JDK 25 / `javac` / Gradle / akses jaringan). Kompilasi dan uji runtime dilakukan via GitHub Actions (`.github/workflows/build.yml`).
- **Tindakan Pencegahan Statis & Verifikasi Javadoc Paper 26.3:**
  1. `GameRule.DO_DAYLIGHT_CYCLE` → `GameRules.ADVANCE_TIME` (`26.3.build.242-stable`).
  2. `Attribute.GENERIC_*` → `Attribute.MOVEMENT_SPEED`, `Attribute.MAX_HEALTH`, `Attribute.FOLLOW_RANGE`, `Attribute.SPAWN_REINFORCEMENTS`, `Attribute.KNOCKBACK_RESISTANCE` (`26.3.build.230-stable`).
  3. `AttributeModifier` menggunakan constructor berbasis `NamespacedKey`.
  4. Registrasi command Brigadier menggunakan `LifecycleEvents.COMMANDS` + `ArgumentTypes.players()` / `ArgumentTypes.finePosition()` (`26.3.build.253-stable`).
  5. Seluruh 4 `TODO-VERIFY` diselesaikan melalui Javadoc resmi Paper 26.3 (`Mob#setDespawnInPeacefulOverride`, `ItemStack.serializeItemsAsBytes`/`deserializeItemsFromBytes`, Adventure `Sound.sound(Key.key(...))`).

## Iterasi 0.1 — Audit Kode & Perbaikan 9 Bug (`BUG-1` s.d. `BUG-9`)

- **BUG-1 (Tes palsu):** `HordeManager.spawnWave` direfaktor ke `spawnWaveCore(Location, @Nullable Player, UUID, HordePlayerState, int, String)`; `buildSpawnDataCore`, `startHordeCore`, dan `tickState` diekspos agar `AT-02`, `AT-09`, `AT-10`, `AT-12`, `AT-15`, `AT-20`, dan `AT-23` menguji jalur eksekusi plugin sungguhan (termasuk event `PlayerBedEnterEvent` 4-argumen Paper 26.3 dengan `BedEnterAction`).
- **BUG-2 (Server uji headless tanpa pemain):** `AcceptanceTests` menahan area `15x15` chunk dengan `World#addPluginChunkTicket` + `setChunkForceLoaded` lalu melepasnya di akhir; `AT-18` & `AT-19` memeriksa `Entity#getTicksLived()` selama 20 tick dan mengembalikan `[INFO] ... SKIPPED` bila entitas tidak di-tick.
- **BUG-3 (CI & skrip gate):** `.github/workflows/build.yml`, `run_all.sh`, dan `run_all.ps1` kini memverifikasi setiap ID tes wajib secara eksplisit, mendukung `VERIFIED` / `VERIFIED-PARTIAL` / `UNVERIFIED`, dan mengunggah artefak `build-log`.
- **BUG-4 (Pin versi Paper API):** Dipin tetap ke `io.papermc.paper:paper-api:26.3.build.255-stable` (tanpa `+`), didukung bukti header Javadoc resmi PaperMC 26.3.
- **BUG-5 (Door breaking berbasis tick):** Mengganti penghitung kunjungan dengan `Map<BlockKey, DoorProgress(firstTick, lastTick)>` berbasis `Bukkit.getCurrentTick()` (`>= 40` tick, gap `<= 10` tick, pembersihan `> 100` tick, maks 4 pintu/detik).
- **BUG-6 (Chicken jockey R1):** `spawnHordeZombieAt` menggunakan `World#spawn(..., SpawnReason.CUSTOM, false, null)` (`randomizeData=false`) + `vehicle.remove()` + penghapusan passenger; `AT-16` menguji 200 spawn berturut-turut dan 0 entitas non-zombie dalam radius 3 blok.
- **BUG-7 (Performa global cap):** `countGlobalHordeZombies()` dihitung sekali per gelombang/batch lalu diinkremen lokal.
- **BUG-8 (Tes beban 100 tick):** `AT-08` (100 mob) dan `AT-21` (200 horde zombie) menunggu 100 tick dalam keadaan aktif lalu mencatat `Bukkit.getAverageTickTime()` (MSPT) dan `Bukkit.getTPS()` sebagai `[INFO]`.
- **BUG-9 (Sinkronisasi dokumen):** Seluruh dokumen (`README.md`, `PORTING_REPORT.md`, `parity-matrix.md`, `DEBUG_LOG.md`, `CONFIG_MAPPING.md`) disinkronkan dengan kode terbaru.

## Iterasi 1+ — Menunggu Hasil GitHub Actions (`build-log` & `neon-report`)

Setelah workflow GitHub Actions selesai, setiap kegagalan kompilasi/runtime/AT (bila ada) akan dicatat dan diperbaiki dengan patch minimal sesuai PRD FR-05.
