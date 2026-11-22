# Pemetaan Konfigurasi — The Hordes (1.21.1-1.6.3f) → HordesPaper (Paper 26.3, PRD v3)

Nilai default asli mod **Terverifikasi** dari bytecode `The-Hordes-1.21.1-1.6.3f.jar`. Revisi **PRD v3 (R1, R2, R3)** ditandai secara eksplisit di kolom catatan; seluruh default mod lainnya **TERKUNCI**.

## 1. Horde Event (`config.yml` → `horde-event.*`)

| Opsi Mod (NeoForge) | Default Mod \[JAR\] | Default Plugin (`config.yml`) | Opsi Plugin (`config.yml`) | Catatan Perilaku |
| --- | --- | --- | --- | --- |
| `enableHordeEvent` | `true` | `true` | `horde-event.enabled` | Terkunci |
| `hordesCommandOnly` | `false` | `false` | `horde-event.command-only` | Terkunci |
| `spawnAmount` | `15` | **`25`** *(R2)* | `horde-event.spawn-amount` | **PRD v3 R2:** 25 zombie per gelombang |
| `hordeSpawnMultiplier` | `1.05` | `1.05` | `horde-event.spawn-multiplier` | Terkunci |
| `hordeSpawnDuration` | `6000` | `6000` | `horde-event.spawn-duration-ticks` | Terkunci (5 menit) |
| `hordeSpawnInterval` | `600` | **`200`** *(R2)* | `horde-event.spawn-interval-ticks` | **PRD v3 R2:** gelombang tiap 10 detik (200 tick) |
| — *(baru R2)* | — | **`5`** *(R2)* | `horde-event.spawn-batch-size` | Jumlah zombie per batch dalam satu gelombang |
| — *(baru R2)* | — | **`4`** *(R2)* | `horde-event.spawn-batch-delay-ticks` | Jeda antar batch (5 zombie tiap 4 tick = 25 dalam 16 tick) |
| `hordeStartTime` | `18000` | `18000` | `horde-event.start-time-ticks` | Terkunci (tengah malam) |
| `hordeSpawnDays` | `10` | `10` | `horde-event.spawn-days` | Terkunci |
| `hordeSpawnVariation` | `0` | `0` | `horde-event.spawn-variation-days` | Terkunci |
| `hordeSpawnMax` | `160` | **`200`** *(R2)* | `horde-event.spawn-max` | **PRD v3 R2:** batas aktif per pemain |
| — *(baru R2)* | — | **`400`** *(R2)* | `horde-event.global-zombie-cap` | **PRD v3 R2:** batas total horde zombie lintas dunia |
| `dayLength` | `24000` | `24000` | `horde-event.day-length-ticks` | Terkunci |
| `hordePathingInterval` | `25` | `25` | `horde-event.pathing-interval-ticks` | Terkunci |
| `hordeEntitySpeed` | `1.0` | `1.0` | `horde-event.entity-speed` | Terkunci |
| `spawnFirstDay` | `false` | `false` | `horde-event.spawn-first-day` | Terkunci |
| `canSleepDuringHorde` | `false` | `false` | `horde-event.can-sleep-during-horde` | Terkunci |
| `hordePreventsOtherPlayersSleeping` | `true` | `true` | `horde-event.prevent-other-players-sleeping` | Terkunci |
| `hordeMultiplayerScaling` | `0.8` | `0.8` | `horde-event.multiplayer-scaling` | Terkunci |
| `pauseEventServer` | `true` | `true` | `horde-event.pause-when-empty` | Terkunci (`GameRules.ADVANCE_TIME`) |
| `hordeEventByPlayerTime` | `true` | `true` | `horde-event.track-by-player-time` | Terkunci |
| `hordeStartBuffer` | `1200` | `1200` | `horde-event.start-buffer-ticks` | Terkunci |
| `hordeSpawnChecks` | `25` | `25` | `horde-event.spawn-checks` | Terkunci |
| `hordeSpawnDistance` | `75.0` | `75.0` | `horde-event.spawn-distance` | Terkunci |

## 2. Horde Zombie Gila — PRD v3 R3 (`config.yml` → `horde-zombie.*`)

| Opsi Plugin (`config.yml`) | Default | Catatan Perilaku (PRD v3 Bagian 4) |
| --- | --- | --- |
| `horde-zombie.speed-multiplier` | `1.6` | `Attribute.MOVEMENT_SPEED` via `AttributeModifier` (`MULTIPLY_SCALAR_1`, delta `0.6`) |
| `horde-zombie.follow-range` | `100.0` | `Attribute.FOLLOW_RANGE` minimal `max(follow-range, spawn-distance + 25)` |
| `horde-zombie.target-lock` | `true` | Memblokir perpindahan target ke non-pemain via `EntityTargetEvent` |
| `horde-zombie.break-doors` | `true` | `Zombie#setCanBreakDoors(true)` + fallback berbasis tick (`40` tick, maks `4` pintu/detik) di non-Hard |
| `horde-zombie.no-sunburn` | `true` | `Zombie#setShouldBurnInDay(false)` |
| `horde-zombie.no-baby` | `true` | `World#spawn(..., randomizeData=false)` + `Zombie#setAdult()` + hapus vehicle/passenger |
| `horde-zombie.no-reinforcements` | `true` | `Attribute.SPAWN_REINFORCEMENTS` diset `0.0` |
| `horde-zombie.health-multiplier` | `1.0` | Pengali `Attribute.MAX_HEALTH` |
| `horde-zombie.knockback-resistance` | `0.2` | `Attribute.KNOCKBACK_RESISTANCE` |
| `horde-zombie.equipment` | `"none"` | Menghapus seluruh armor/senjata acak |
| `horde-zombie.drops-multiplier` | `0.5` | Pengurangan drop saat mati untuk menekan lag |
| `horde-zombie.xp-multiplier` | `0.5` | Pengurangan orb XP saat mati untuk menekan lag |

## 3. Infection (`config.yml` → `infection.*`) — Terkunci

| Opsi Mod (NeoForge) | Default Mod \[JAR\] | Opsi Plugin (`config.yml`) | Catatan Perilaku |
| --- | --- | --- | --- |
| `enableMobInfection` | `true` | `infection.enabled` | Terkunci |
| `infectPlayers` | `true` | `infection.infect-players` | Terkunci |
| `playerInfectionResistance` | `0.25` | `infection.player-base-resistance` | Terkunci |
| `ticksForEffectStage` | `6000` | `infection.ticks-per-stage` | Terkunci (4 fase = 24000 tick awal) |
| `infectSlowness` | `true` | `infection.slowness-enabled` | Terkunci (`-10%` `MULTIPLY_SCALAR_1`) |
| `infectHunger` | `true` | `infection.hunger-enabled` | Terkunci (`0.03f` exhaustion) |
| `infectionSpawnsZombiePlayers` | `true` | `infection.spawn-zombie-players` | Terkunci |
| `infectionEntitiesAggroConversions` | `true` | `infection.entities-aggro-conversions` | Terkunci |
| `effectStageTickReduction` | `0.95` | `infection.stage-tick-reduction` | Terkunci (`0.95^n`, reset saat mati) |

## 4. Zombie Players (`config.yml` → `zombie-players.*`)

| Opsi Mod (NeoForge) | Default Mod \[JAR\] | Default Plugin | Opsi Plugin (`config.yml`) | Catatan Perilaku |
| --- | --- | --- | --- | --- |
| `zombieGraves` | `false` | `false` | `zombie-players.graves-always` | Terkunci |
| `drownedPlayers` | `true` | **`false`** *(R1)* | `zombie-players.drowned-players` | **PRD v3 R1:** zombie biasa saja |
| `huskPlayers` | `true` | **`false`** *(R1)* | `zombie-players.husk-players` | **PRD v3 R1:** zombie biasa saja |
| `zombiePlayersFireImmune` | `false` | `false` | `zombie-players.fire-immune` | Terkunci |
| `zombiePlayersBurn` | `false` | `false` | `zombie-players.burn-in-sunlight` | Terkunci |
| `zombiePlayersOnlyHurtByPlayers` | `false` | `false` | `zombie-players.only-hurt-by-players` | Terkunci |
| `zombiePlayersStoreItems` | `true` | `true` | `zombie-players.store-items` | Terkunci (`ItemStack.serializeItemsAsBytes`) |
| `zombiePlayersDespawnPeaceful` | `false` | `false` | `zombie-players.despawn-in-peaceful` | Terkunci (`Mob#setDespawnInPeacefulOverride`) |

## 5. Misc (`config.yml` → `misc.*`) — Terkunci

| Opsi Mod (NeoForge) | Default Mod \[JAR\] | Opsi Plugin (`config.yml`) | Catatan Perilaku |
| --- | --- | --- | --- |
| `zombiesBurn` | `false` | `misc.zombies-burn` | Terkunci (default mod `false`) |
| `skeletonsBurn` | `false` | `misc.skeletons-burn` | Terkunci |
| `zombieVillagersCanBeCured` | `false` | `misc.zombie-villagers-can-be-cured` | Terkunci |
| `piglinsHoglinsConvert` | `false` | `misc.piglins-hoglins-convert` | Terkunci |
| `aggressiveZombieHorses` | `true` | `misc.aggressive-zombie-horses` | Terkunci |
| `zombieHorsesBurn` | `false` | `misc.zombie-horses-burn` | Terkunci |
| `skeletonHorsesBurn` | `false` | `misc.skeleton-horses-burn` | Terkunci |
| `zombiesScareHorses` | `true` | `misc.zombies-scare-horses` | Terkunci |
| `zombiePiglinsHostile` | `true` | `misc.zombie-piglins-hostile` | Terkunci |
| `piglinsHuntZombies` | `true` | `misc.piglins-hunt-zombies` | Terkunci |
| `piglinsCureThemself` | `true` | `misc.piglins-cure-themselves` | Terkunci (parsial tanpa NMS) |
| `illagersHuntZombies` | `true` | `misc.illagers-hunt-zombies` | Terkunci |
| `zoglinsAttackUndead` | `false` | `misc.zoglins-attack-undead` | Terkunci |
| `zoglinsAttackMobs` | `true` | `misc.zoglins-attack-mobs` | Terkunci |

## 6. File YAML Data Plugin

| Data Bawaan Mod | File Plugin | Catatan |
| --- | --- | --- |
| `horde_data/tables/*.json` | `tables.yml` | **PRD v3 R1:** dikunci ke `hordes:default` dengan satu entri `ZOMBIE` dewasa |
| `horde_data/scripts/default.json` | `scripts.yml` | Kondisi `biome`, `day`, `random`, `not`; aksi `set_spawn_type` (`set_spawntable` dikunci R1) |
| `infection/*.json` | `infection.yml` | Infector, konversi, proteksi armor, `ENCHANTED_GOLDEN_APPLE: 150`, cure, bioma husk |
