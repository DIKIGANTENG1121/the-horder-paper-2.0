package net.neon.hordes.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class HordesConfig {

    public record ConversionSpec(EntityType convertsTo, double protection, Double maxHealth, String customName) {}
    public record WearableProtection(double protection, String operation) {}

    private final JavaPlugin plugin;

    // Debug
    public boolean debug;

    // Horde Event (locked defaults + R2 wave/batch/cap options)
    public boolean enableHordeEvent;
    public boolean hordesCommandOnly;
    public int spawnAmount;
    public double hordeSpawnMultiplier;
    public int hordeSpawnDuration;
    public int hordeSpawnInterval;
    public int spawnBatchSize;
    public int spawnBatchDelayTicks;
    public int hordeStartTime;
    public int hordeSpawnDays;
    public int hordeSpawnVariation;
    public int hordeSpawnMax;
    public int globalZombieCap;
    public int dayLength;
    public int hordePathingInterval;
    public double hordeEntitySpeed;
    public boolean spawnFirstDay;
    public boolean canSleepDuringHorde;
    public boolean hordePreventsOtherPlayersSleeping;
    public double hordeMultiplayerScaling;
    public double hordeMultiplayerRadius;
    public boolean pauseEventServer;
    public boolean hordeEventByPlayerTime;
    public int hordeStartBuffer;
    public int hordeSpawnChecks;
    public double hordeSpawnDistance;
    public String startSound;
    public String startMessage;
    public String endMessage;
    public String cannotSleepMessage;
    public String otherHordeSleepMessage;
    public List<String> rewardCommands = List.of();

    // R3: Horde Zombie tuning (section horde-zombie)
    public double hordeZombieSpeedMultiplier;
    public double hordeZombieFollowRange;
    public boolean hordeZombieTargetLock;
    public boolean hordeZombieBreakDoors;
    public boolean hordeZombieNoSunburn;
    public boolean hordeZombieNoBaby;
    public boolean hordeZombieNoReinforcements;
    public double hordeZombieHealthMultiplier;
    public double hordeZombieKnockbackResistance;
    public String hordeZombieEquipment;
    public double hordeZombieDropsMultiplier;
    public double hordeZombieXpMultiplier;

    // Infection (locked)
    public boolean enableMobInfection;
    public boolean infectPlayers;
    public double playerInfectionResistance;
    public int ticksForEffectStage;
    public boolean infectSlowness;
    public boolean infectHunger;
    public boolean infectionSpawnsZombiePlayers;
    public boolean infectionEntitiesAggroConversions;
    public double effectStageTickReduction;
    public boolean showBossbar;
    public boolean showActionbar;
    public boolean spawnParticles;

    // Zombie Players (R1 assumption: drowned/husk default false)
    public boolean zombieGraves;
    public boolean drownedPlayers;
    public boolean huskPlayers;
    public boolean zombiePlayersFireImmune;
    public boolean zombiePlayersBurn;
    public boolean zombiePlayersOnlyHurtByPlayers;
    public boolean zombiePlayersStoreItems;
    public boolean zombiePlayersDespawnPeaceful;

    // Misc (locked)
    public boolean zombiesBurn;
    public boolean skeletonsBurn;
    public boolean zombieVillagersCanBeCured;
    public boolean piglinsHoglinsConvert;
    public boolean aggressiveZombieHorses;
    public boolean zombieHorsesBurn;
    public boolean skeletonHorsesBurn;
    public boolean zombiesScareHorses;
    public boolean zombiePiglinsHostile;
    public boolean piglinsHuntZombies;
    public boolean piglinsCureThemself;
    public boolean illagersHuntZombies;
    public boolean zoglinsAttackUndead;
    public boolean zoglinsAttackMobs;

    // Data files
    public final Map<String, List<SpawnTableEntry>> spawnTables = new HashMap<>();
    public final List<ScriptRule> scriptRules = new ArrayList<>();
    public final Map<EntityType, Double> infectors = new EnumMap<>(EntityType.class);
    public final Map<EntityType, ConversionSpec> conversions = new EnumMap<>(EntityType.class);
    public final Map<Material, WearableProtection> wearablesProtection = new EnumMap<>(Material.class);
    public final Map<Material, Integer> immunityItemsSeconds = new EnumMap<>(Material.class);
    public final Set<Material> cures = EnumSet.noneOf(Material.class);
    public final Set<String> huskBiomes = new HashSet<>();

    public HordesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        saveResourceIfAbsent("tables.yml");
        saveResourceIfAbsent("scripts.yml");
        saveResourceIfAbsent("infection.yml");

        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        this.debug = cfg.getBoolean("debug", false);

        // Horde Event (updated defaults: 5-10 per player every 100 ticks / 5s, radius 64.0)
        this.enableHordeEvent = cfg.getBoolean("horde-event.enabled", true);
        this.hordesCommandOnly = cfg.getBoolean("horde-event.command-only", false);
        this.spawnAmount = Math.max(1, cfg.getInt("horde-event.spawn-amount", 10));
        this.hordeSpawnMultiplier = cfg.getDouble("horde-event.spawn-multiplier", 1.05);
        this.hordeSpawnDuration = Math.max(20, cfg.getInt("horde-event.spawn-duration-ticks", 6000));
        this.hordeSpawnInterval = Math.max(20, cfg.getInt("horde-event.spawn-interval-ticks", 100));
        this.spawnBatchSize = Math.max(1, cfg.getInt("horde-event.spawn-batch-size", 5));
        this.spawnBatchDelayTicks = Math.max(1, cfg.getInt("horde-event.spawn-batch-delay-ticks", 4));
        this.hordeStartTime = cfg.getInt("horde-event.start-time-ticks", 18000);
        this.hordeSpawnDays = Math.max(1, cfg.getInt("horde-event.spawn-days", 10));
        this.hordeSpawnVariation = Math.max(0, cfg.getInt("horde-event.spawn-variation-days", 0));
        this.hordeSpawnMax = Math.max(1, cfg.getInt("horde-event.spawn-max", 200));
        this.globalZombieCap = Math.max(1, cfg.getInt("horde-event.global-zombie-cap", 400));
        this.dayLength = Math.max(1200, cfg.getInt("horde-event.day-length-ticks", 24000));
        this.hordePathingInterval = Math.max(5, cfg.getInt("horde-event.pathing-interval-ticks", 25));
        this.hordeEntitySpeed = cfg.getDouble("horde-event.entity-speed", 1.0);
        this.spawnFirstDay = cfg.getBoolean("horde-event.spawn-first-day", false);
        this.canSleepDuringHorde = cfg.getBoolean("horde-event.can-sleep-during-horde", false);
        this.hordePreventsOtherPlayersSleeping = cfg.getBoolean("horde-event.prevent-other-players-sleeping", true);
        this.hordeMultiplayerScaling = cfg.getDouble("horde-event.multiplayer-scaling", 1.0);
        this.hordeMultiplayerRadius = cfg.getDouble("horde-event.multiplayer-radius", 64.0);
        this.pauseEventServer = cfg.getBoolean("horde-event.pause-when-empty", true);
        this.hordeEventByPlayerTime = cfg.getBoolean("horde-event.track-by-player-time", true);
        this.hordeStartBuffer = cfg.getInt("horde-event.start-buffer-ticks", 1200);
        this.hordeSpawnChecks = Math.max(1, cfg.getInt("horde-event.spawn-checks", 25));
        this.hordeSpawnDistance = Math.max(8.0, cfg.getDouble("horde-event.spawn-distance", 75.0));
        this.startSound = cfg.getString("horde-event.start-sound", "minecraft:entity.wolf.howl");
        this.startMessage = cfg.getString("horde-event.start-message", "<red>A horde is approaching!");
        this.endMessage = cfg.getString("horde-event.end-message", "<green>The horde has subsided.");
        this.cannotSleepMessage = cfg.getString("horde-event.cannot-sleep-message", "<red>You cannot sleep while a horde is active!");
        this.otherHordeSleepMessage = cfg.getString("horde-event.other-horde-sleep-message", "<red>You cannot sleep while another player's horde is active!");
        this.rewardCommands = cfg.getStringList("horde-event.reward-commands");

        // R3: Horde Zombie tuning (clamped to safe bounds)
        double rawSpeed = cfg.getDouble("horde-zombie.speed-multiplier", 1.6);
        this.hordeZombieSpeedMultiplier = Math.max(1.0, Math.min(2.2, rawSpeed));
        this.hordeZombieFollowRange = Math.max(16.0, cfg.getDouble("horde-zombie.follow-range", this.hordeSpawnDistance + 25.0));
        this.hordeZombieTargetLock = cfg.getBoolean("horde-zombie.target-lock", true);
        this.hordeZombieBreakDoors = cfg.getBoolean("horde-zombie.break-doors", true);
        this.hordeZombieNoSunburn = cfg.getBoolean("horde-zombie.no-sunburn", true);
        this.hordeZombieNoBaby = cfg.getBoolean("horde-zombie.no-baby", true);
        this.hordeZombieNoReinforcements = cfg.getBoolean("horde-zombie.no-reinforcements", true);
        this.hordeZombieHealthMultiplier = Math.max(0.1, cfg.getDouble("horde-zombie.health-multiplier", 1.0));
        this.hordeZombieKnockbackResistance = Math.max(0.0, Math.min(1.0, cfg.getDouble("horde-zombie.knockback-resistance", 0.2)));
        this.hordeZombieEquipment = cfg.getString("horde-zombie.equipment", "none");
        this.hordeZombieDropsMultiplier = Math.max(0.0, Math.min(1.0, cfg.getDouble("horde-zombie.drops-multiplier", 0.5)));
        this.hordeZombieXpMultiplier = Math.max(0.0, Math.min(1.0, cfg.getDouble("horde-zombie.xp-multiplier", 0.5)));

        // Infection
        this.enableMobInfection = cfg.getBoolean("infection.enabled", true);
        this.infectPlayers = cfg.getBoolean("infection.infect-players", true);
        this.playerInfectionResistance = cfg.getDouble("infection.player-base-resistance", 0.25);
        this.ticksForEffectStage = Math.max(20, cfg.getInt("infection.ticks-per-stage", 6000));
        this.infectSlowness = cfg.getBoolean("infection.slowness-enabled", true);
        this.infectHunger = cfg.getBoolean("infection.hunger-enabled", true);
        this.infectionSpawnsZombiePlayers = cfg.getBoolean("infection.spawn-zombie-players", true);
        this.infectionEntitiesAggroConversions = cfg.getBoolean("infection.entities-aggro-conversions", true);
        this.effectStageTickReduction = cfg.getDouble("infection.stage-tick-reduction", 0.95);
        this.showBossbar = cfg.getBoolean("infection.show-bossbar", true);
        this.showActionbar = cfg.getBoolean("infection.show-actionbar", true);
        this.spawnParticles = cfg.getBoolean("infection.spawn-particles", true);

        // Zombie Players
        this.zombieGraves = cfg.getBoolean("zombie-players.graves-always", false);
        this.drownedPlayers = cfg.getBoolean("zombie-players.drowned-players", false);
        this.huskPlayers = cfg.getBoolean("zombie-players.husk-players", false);
        this.zombiePlayersFireImmune = cfg.getBoolean("zombie-players.fire-immune", false);
        this.zombiePlayersBurn = cfg.getBoolean("zombie-players.burn-in-sunlight", false);
        this.zombiePlayersOnlyHurtByPlayers = cfg.getBoolean("zombie-players.only-hurt-by-players", false);
        this.zombiePlayersStoreItems = cfg.getBoolean("zombie-players.store-items", true);
        this.zombiePlayersDespawnPeaceful = cfg.getBoolean("zombie-players.despawn-in-peaceful", false);

        // Misc
        this.zombiesBurn = cfg.getBoolean("misc.zombies-burn", false);
        this.skeletonsBurn = cfg.getBoolean("misc.skeletons-burn", false);
        this.zombieVillagersCanBeCured = cfg.getBoolean("misc.zombie-villagers-can-be-cured", false);
        this.piglinsHoglinsConvert = cfg.getBoolean("misc.piglins-hoglins-convert", false);
        this.aggressiveZombieHorses = cfg.getBoolean("misc.aggressive-zombie-horses", true);
        this.zombieHorsesBurn = cfg.getBoolean("misc.zombie-horses-burn", false);
        this.skeletonHorsesBurn = cfg.getBoolean("misc.skeleton-horses-burn", false);
        this.zombiesScareHorses = cfg.getBoolean("misc.zombies-scare-horses", true);
        this.zombiePiglinsHostile = cfg.getBoolean("misc.zombie-piglins-hostile", true);
        this.piglinsHuntZombies = cfg.getBoolean("misc.piglins-hunt-zombies", true);
        this.piglinsCureThemself = cfg.getBoolean("misc.piglins-cure-themselves", true);
        this.illagersHuntZombies = cfg.getBoolean("misc.illagers-hunt-zombies", true);
        this.zoglinsAttackUndead = cfg.getBoolean("misc.zoglins-attack-undead", false);
        this.zoglinsAttackMobs = cfg.getBoolean("misc.zoglins-attack-mobs", true);

        loadTables();
        loadScripts();
        loadInfectionData();
    }

    private void saveResourceIfAbsent(String name) {
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private void loadTables() {
        spawnTables.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "tables.yml"));
        ConfigurationSection tablesSec = yml.getConfigurationSection("tables");
        if (tablesSec != null) {
            for (String tableId : tablesSec.getKeys(false)) {
                List> rawList = tablesSec.getMapList(tableId);
                List<SpawnTableEntry> entries = new ArrayList<>();
                for (Map<?, ?> map : rawList) {
                    EntityType type = parseEntityType(String.valueOf(map.get("entity")));
                    // R1 lock: only plain ZOMBIE allowed in horde spawn tables (no passengers/equipment)
                    if (type != EntityType.ZOMBIE) {
                        plugin.getLogger().warning("R1 lock: ignoring non-ZOMBIE entry '" + map.get("entity") + "' in table " + tableId);
                        continue;
                    }
                    int weight = map.containsKey("weight") ? ((Number) map.get("weight")).intValue() : 100;
                    int firstDay = map.containsKey("first-day") ? ((Number) map.get("first-day")).intValue() : 0;
                    int lastDay = map.containsKey("last-day") ? ((Number) map.get("last-day")).intValue() : 0;
                    entries.add(new SpawnTableEntry(EntityType.ZOMBIE, weight, firstDay, lastDay, null, null, List.of()));
                }
                if (!entries.isEmpty()) {
                    spawnTables.put(tableId, List.copyOf(entries));
                }
            }
        }
        // Guarantee locked ZOMBIE-only default & fallback tables
        List<SpawnTableEntry> zombieOnly = List.of(new SpawnTableEntry(EntityType.ZOMBIE, 100, 0, 0, null, null, List.of()));
        spawnTables.putIfAbsent("hordes:default", zombieOnly);
        spawnTables.putIfAbsent("hordes:fallback", zombieOnly);
    }

    private void loadScripts() {
        scriptRules.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "scripts.yml"));
        List> rawRules = yml.getMapList("rules");
        for (Map<?, ?> ruleMap : rawRules) {
            String id = ruleMap.containsKey("id") ? String.valueOf(ruleMap.get("id")) : "rule";
            List<ScriptRule.Condition> conditions = new ArrayList<>();
            if (ruleMap.get("conditions") instanceof List<?> condList) {
                for (Object co : condList) {
                    if (co instanceof Map<?, ?> cm) {
                        ScriptRule.Condition parsed = parseCondition(cm);
                        if (parsed != null) conditions.add(parsed);
                    }
                }
            }
            String setType = null;
            if (ruleMap.get("actions") instanceof Map<?, ?> actMap) {
                if (actMap.get("set_spawn_type") != null) setType = String.valueOf(actMap.get("set_spawn_type"));
            }
            // R1 lock: scripts cannot switch away from hordes:default (ZOMBIE-only)
            scriptRules.add(new ScriptRule(id, List.copyOf(conditions), "hordes:default", setType));
        }
    }

    private ScriptRule.Condition parseCondition(Map<?, ?> map) {
        String type = map.get("type") != null ? String.valueOf(map.get("type")).toLowerCase(Locale.ROOT) : "";
        return switch (type) {
            case "biome" -> {
                Set<String> names = new HashSet<>();
                if (map.get("values") instanceof List<?> vals) {
                    for (Object v : vals) names.add(String.valueOf(v).toUpperCase(Locale.ROOT));
                }
                yield new ScriptRule.BiomeCondition(Set.copyOf(names));
            }
            case "day" -> {
                Integer min = map.get("min") instanceof Number n ? n.intValue() : null;
                Integer max = map.get("max") instanceof Number n ? n.intValue() : null;
                yield new ScriptRule.DayCondition(min, max);
            }
            case "random" -> {
                double chance = map.get("chance") instanceof Number n ? n.doubleValue() : 0.5;
                yield new ScriptRule.RandomCondition(chance);
            }
            case "not" -> {
                ScriptRule.Condition inner = (map.get("condition") instanceof Map<?, ?> innerMap)
                        ? parseCondition(innerMap) : null;
                yield new ScriptRule.NotCondition(inner);
            }
            default -> null;
        };
    }

    private void loadInfectionData() {
        infectors.clear();
        conversions.clear();
        wearablesProtection.clear();
        immunityItemsSeconds.clear();
        cures.clear();
        huskBiomes.clear();

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "infection.yml"));

        ConfigurationSection infSec = yml.getConfigurationSection("infectors");
        if (infSec != null) {
            for (String key : infSec.getKeys(false)) {
                EntityType et = parseEntityType(key);
                if (et != null) infectors.put(et, infSec.getDouble(key));
            }
        }

        ConfigurationSection convSec = yml.getConfigurationSection("conversions");
        if (convSec != null) {
            for (String key : convSec.getKeys(false)) {
                EntityType src = parseEntityType(key);
                ConfigurationSection entry = convSec.getConfigurationSection(key);
                if (src == null || entry == null) continue;
                EntityType dst = parseEntityType(entry.getString("converts-to"));
                if (dst == null) continue;
                double prot = entry.getDouble("protection", 0.0);
                Double maxHp = entry.contains("max-health") ? entry.getDouble("max-health") : null;
                String cname = entry.getString("custom-name", null);
                conversions.put(src, new ConversionSpec(dst, prot, maxHp, cname));
            }
        }

        ConfigurationSection wpSec = yml.getConfigurationSection("wearables-protection");
        if (wpSec != null) {
            for (String key : wpSec.getKeys(false)) {
                Material mat = parseMaterial(key);
                ConfigurationSection entry = wpSec.getConfigurationSection(key);
                if (mat != null && entry != null) {
                    wearablesProtection.put(mat, new WearableProtection(
                            entry.getDouble("protection", 0.0),
                            entry.getString("operation", "add_multiplied_total")));
                }
            }
        }

        ConfigurationSection immSec = yml.getConfigurationSection("immunity-items");
        if (immSec != null) {
            for (String key : immSec.getKeys(false)) {
                Material mat = parseMaterial(key);
                if (mat != null) immunityItemsSeconds.put(mat, immSec.getInt(key, 150));
            }
        }

        for (String cureName : yml.getStringList("cures")) {
            Material mat = parseMaterial(cureName);
            if (mat != null) cures.add(mat);
        }

        for (String biomeName : yml.getStringList("husk-biomes")) {
            huskBiomes.add(biomeName.toUpperCase(Locale.ROOT));
        }
    }

    private EntityType parseEntityType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Unknown EntityType in config: " + raw);
            return null;
        }
    }

    private Material parseMaterial(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || s.equalsIgnoreCase("null")) return null;
        return Material.matchMaterial(s);
    }
}
