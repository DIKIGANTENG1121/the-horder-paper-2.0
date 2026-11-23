package net.neon.hordes.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.neon.hordes.api.event.*;
import net.neon.hordes.compat.Paper26Compat;
import net.neon.hordes.config.HordesConfig;
import net.neon.hordes.config.ScriptRule;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages F-01, F-02, F-07, F-10, F-12, F-16, simplified F-17, and PRD v3 revisions R1, R2, R3.
 */
public final class HordeManager {

    public static final int MAX_PATH_UPDATES_PER_TICK = 20;
    private static final int DOOR_BREAK_REQUIRED_TICKS = 40;
    private static final int DOOR_BREAK_MAX_GAP_TICKS = 10;
    private static final int DOOR_BREAK_STALE_TICKS = 100;
    private static final int MAX_DOOR_BREAKS_PER_SECOND = 4;
    private static final int MIN_ZOMBIES_PER_PLAYER = 5;
    private static final int MAX_ZOMBIES_PER_PLAYER = 10;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private record BlockKey(UUID worldUid, int x, int y, int z) {}
    private record DoorProgress(int firstTick, int lastTick) {}

    private final JavaPlugin plugin;
    private final HordesConfig config;
    private final HordesKeys keys;
    private final Random random = new Random();
    private final Map<UUID, HordePlayerState> states = new HashMap<>();
    private final ArrayDeque<UUID> pathfindingQueue = new ArrayDeque<>();
    private final Map<BlockKey, DoorProgress> doorBreakProgress = new HashMap<>();
    private final File dataFile;

    private boolean pausedByEmptyServer = false;
    private int doorBreaksThisSecond = 0;
    private int doorSecondTickCounter = 0;

    public HordeManager(JavaPlugin plugin, HordesConfig config, HordesKeys keys) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
        this.dataFile = new File(plugin.getDataFolder(), "horde-data.yml");
    }

    public void checkSimulationDistanceWarning() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            int simChunks = world.getSimulationDistance();
            int safeRadius = simChunks * 16 - 16;
            if (config.hordeSpawnDistance >= safeRadius) {
                plugin.getLogger().warning(String.format(
                        "World '%s' simulation-distance=%d chunks (active radius ~%d blocks) is too small for spawn-distance=%.1f. "
                                + "Horde zombies may freeze until players move closer. Increase simulation-distance or lower spawn-distance.",
                        world.getName(), simChunks, safeRadius, config.hordeSpawnDistance));
            }
        }
    }

    public HordePlayerState getOrCreateState(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), uuid -> {
            int firstDay = config.spawnFirstDay ? 0 : config.hordeSpawnDays;
            if (config.hordeSpawnVariation > 0) {
                firstDay += random.nextInt(config.hordeSpawnVariation + 1);
            }
            return new HordePlayerState(uuid, player.getName(), firstDay);
        });
    }

    public @Nullable HordePlayerState getState(UUID playerId) {
        return states.get(playerId);
    }

    public void registerState(HordePlayerState state) {
        states.put(state.getPlayerId(), state);
    }

    public void removeState(UUID playerId) {
        states.remove(playerId);
    }

    public void clearStatesInMemory() {
        states.clear();
    }

    public Collection<HordePlayerState> getAllStates() {
        return Collections.unmodifiableCollection(states.values());
    }

    public boolean isAnyHordeActive() {
        for (HordePlayerState state : states.values()) {
            if (state.isActive()) return true;
        }
        return false;
    }

    public boolean isPlayerInHorde(Player player) {
        HordePlayerState st = states.get(player.getUniqueId());
        return st != null && st.isActive();
    }

    public boolean isHordeZombie(Entity entity) {
        return entity instanceof Zombie
                && entity.getPersistentDataContainer().has(keys.hordeZombieTag, PersistentDataType.BYTE);
    }

    public @Nullable UUID getHordeOwner(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(keys.hordeOwner, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public int countGlobalHordeZombies() {
        int total = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Zombie z : w.getEntitiesByClass(Zombie.class)) {
                if (z.isValid() && !z.isDead() && isHordeZombie(z)) {
                    total++;
                }
            }
        }
        return total;
    }

    public void tick(int intervalTicks) {
        if (!config.enableHordeEvent) return;

        if (config.pauseEventServer) {
            boolean empty = Bukkit.getOnlinePlayers().isEmpty();
            if (empty && !pausedByEmptyServer) {
                for (World w : Bukkit.getWorlds()) {
                    if (w.getEnvironment() == World.Environment.NORMAL) {
                        Paper26Compat.setDaylightCycle(w, false);
                    }
                }
                pausedByEmptyServer = true;
                return;
            } else if (!empty && pausedByEmptyServer) {
                for (World w : Bukkit.getWorlds()) {
                    if (w.getEnvironment() == World.Environment.NORMAL) {
                        Paper26Compat.setDaylightCycle(w, true);
                    }
                }
                pausedByEmptyServer = false;
            } else if (empty) {
                return;
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            HordePlayerState state = getOrCreateState(player);
            state.setLastKnownName(player.getName());
            tickState(player.getWorld(), player.getLocation(), player, state, intervalTicks);
        }
    }

    public void tickState(World world, Location anchor, @Nullable Player owner, HordePlayerState state, int intervalTicks) {
        if (!config.enableHordeEvent) return;
        state.addPlaytimeTicks(intervalTicks);

        if (state.isActive()) {
            tickActiveHordeCore(anchor, owner, state, intervalTicks);
        } else if (!config.hordesCommandOnly) {
            checkNaturalHordeStartCore(world, anchor, owner, state);
        }
    }

    public void tickPathingAndDoors() {
        int currentTick = Bukkit.getCurrentTick();
        doorSecondTickCounter++;
        if (doorSecondTickCounter >= 20) {
            doorSecondTickCounter = 0;
            doorBreaksThisSecond = 0;
            doorBreakProgress.entrySet().removeIf(e -> (currentTick - e.getValue().lastTick()) > DOOR_BREAK_STALE_TICKS);
        }

        if (pathfindingQueue.isEmpty()) {
            for (HordePlayerState st : states.values()) {
                if (st.isActive()) {
                    pathfindingQueue.addAll(st.getActiveEntities());
                }
            }
        }

        int processed = 0;
        while (processed < MAX_PATH_UPDATES_PER_TICK && !pathfindingQueue.isEmpty()) {
            UUID id = pathfindingQueue.poll();
            Entity raw = Bukkit.getEntity(id);
            if (!(raw instanceof Zombie zombie) || zombie.isDead() || !zombie.isValid()) {
                continue;
            }
            processed++;

            Player target = resolveHordeTarget(zombie);
            if (target == null) continue;

            if (config.hordeZombieTargetLock && !target.equals(zombie.getTarget())) {
                zombie.setTarget(target);
            }

            if (!zombie.getPathfinder().hasPath()) {
                zombie.getPathfinder().moveTo(target, 1.0);
            }

            if (config.hordeZombieBreakDoors
                    && zombie.getWorld().getDifficulty() != Difficulty.HARD
                    && doorBreaksThisSecond < MAX_DOOR_BREAKS_PER_SECOND) {
                tryFallbackBreakDoor(zombie, target, currentTick);
            }
        }
    }

    private @Nullable Player resolveHordeTarget(Zombie zombie) {
        UUID ownerId = getHordeOwner(zombie);
        if (ownerId != null) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && owner.isOnline() && !owner.isDead()
                    && owner.getWorld().equals(zombie.getWorld())
                    && owner.getGameMode() != GameMode.CREATIVE
                    && owner.getGameMode() != GameMode.SPECTATOR) {
                return owner;
            }
        }
        double maxDistSq = config.hordeZombieFollowRange * config.hordeZombieFollowRange;
        Player nearest = null;
        double bestDistSq = maxDistSq;
        for (Player p : zombie.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(zombie.getLocation());
            if (d < bestDistSq) {
                bestDistSq = d;
                nearest = p;
            }
        }
        return nearest;
    }

    private void tryFallbackBreakDoor(Zombie zombie, Player target, int currentTick) {
        Vector dir = target.getLocation().toVector().subtract(zombie.getLocation().toVector());
        if (dir.lengthSquared() < 0.01) return;
        dir.normalize();
        Location checkLoc = zombie.getLocation().add(dir.getX(), 0.5, dir.getZ());
        Block block = checkLoc.getBlock();
        Material type = block.getType();
        if (!Tag.WOODEN_DOORS.isTagged(type) && !Tag.FENCE_GATES.isTagged(type)) {
            return;
        }
        BlockKey key = new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        DoorProgress prev = doorBreakProgress.get(key);
        if (prev == null || (currentTick - prev.lastTick()) > DOOR_BREAK_MAX_GAP_TICKS) {
            doorBreakProgress.put(key, new DoorProgress(currentTick, currentTick));
        } else if ((currentTick - prev.firstTick()) >= DOOR_BREAK_REQUIRED_TICKS) {
            doorBreakProgress.remove(key);
            block.breakNaturally();
            doorBreaksThisSecond++;
        } else {
            doorBreakProgress.put(key, new DoorProgress(prev.firstTick(), currentTick));
        }
    }

    private void checkNaturalHordeStartCore(World world, Location anchor, @Nullable Player owner, HordePlayerState state) {
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        long timeRef = config.hordeEventByPlayerTime ? state.getPlaytimeTicks() : world.getFullTime();
        int currentDay = (int) (timeRef / config.dayLength);
        long timeOfDay = world.getTime() % config.dayLength;

        if (currentDay >= state.getNextHordeDay()
                && timeOfDay >= config.hordeStartTime
                && timeOfDay <= config.hordeStartTime + config.hordeStartBuffer) {
            startHordeCore(anchor, owner, state.getPlayerId(), state, config.hordeSpawnDuration, null, true);
        }
    }

    public HordeSpawnData buildSpawnData(Player player, HordePlayerState state, @Nullable String tableOverride, boolean natural) {
        int nearbyPlayers = 0;
        for (Player other : player.getWorld().getPlayers()) {
            if (!other.equals(player)
                    && other.getLocation().distanceSquared(player.getLocation()) <= config.hordeMultiplayerRadius * config.hordeMultiplayerRadius) {
                nearbyPlayers++;
            }
        }
        return buildSpawnDataCore(player.getLocation(), player, state, tableOverride, natural, nearbyPlayers);
    }

    public HordeSpawnData buildSpawnDataCore(
            Location anchor,
            @Nullable Player player,
            HordePlayerState state,
            @Nullable String tableOverride,
            boolean natural,
            int nearbyPlayers
    ) {
        World world = anchor.getWorld();
        int day = world != null ? getCurrentDay(world, state) : 0;
        int baseAmount = config.spawnAmount;
        if (natural && state.getNaturalHordeCount() > 0) {
            baseAmount = (int) Math.round(config.spawnAmount * Math.pow(config.hordeSpawnMultiplier, state.getNaturalHordeCount()));
        }
        baseAmount = Math.min(baseAmount, config.hordeSpawnMax);

        if (nearbyPlayers > 0) {
            baseAmount = Math.max(1, (int) Math.round(baseAmount * Math.pow(config.hordeMultiplayerScaling, nearbyPlayers)));
        }

        HordeSpawnData data = new HordeSpawnData(
                "hordes:default",
                "avoid_fluids",
                baseAmount,
                config.hordeSpawnDuration,
                config.hordeSpawnInterval,
                config.hordeEntitySpeed,
                config.rewardCommands
        );

        if (world != null) {
            Biome biome = anchor.getBlock().getBiome();
            for (ScriptRule rule : config.scriptRules) {
                if (rule.matches(biome, day, random)) {
                    if (rule.setSpawnType() != null) data.setSpawnType(rule.setSpawnType());
                }
            }
        }

        if (tableOverride != null && !tableOverride.isBlank() && !tableOverride.equals("hordes:default")) {
            if (config.debug) {
                plugin.getLogger().info("R1 lock: overriding requested table '" + tableOverride + "' with 'hordes:default' (ZOMBIE-only).");
            }
        }
        data.setTableId("hordes:default");

        if (player != null) {
            Bukkit.getPluginManager().callEvent(new HordeBuildSpawnDataEvent(player, data));
            data.setTableId("hordes:default");
        }
        return data;
    }

    public boolean startHorde(Player player, int durationTicks, @Nullable String tableOverride, boolean natural) {
        HordePlayerState state = getOrCreateState(player);
        return startHordeCore(player.getLocation(), player, player.getUniqueId(), state, durationTicks, tableOverride, natural);
    }

    public boolean startHordeCore(
            Location anchor,
            @Nullable Player owner,
            UUID ownerId,
            HordePlayerState state,
            int durationTicks,
            @Nullable String tableOverride,
            boolean natural
    ) {
        HordeSpawnData data = owner != null
                ? buildSpawnData(owner, state, tableOverride, natural)
                : buildSpawnDataCore(anchor, null, state, tableOverride, natural, 0);
        data.setDurationTicks(durationTicks > 0 ? durationTicks : config.hordeSpawnDuration);

        if (owner != null) {
            HordeStartEvent startEvent = new HordeStartEvent(owner, data, natural);
            Bukkit.getPluginManager().callEvent(startEvent);
            if (startEvent.isCancelled()) return false;
        }

        state.setActive(true);
        state.setRemainingTicks(data.getDurationTicks());
        state.setWaveCooldownTicks(0);
        state.setActiveTable(data.getTableId());
        state.setActiveSpawnType(data.getSpawnType());
        state.setCurrentWaveAmount(data.getSpawnAmount());

        if (natural && anchor.getWorld() != null) {
            state.incrementNaturalHordeCount();
            int nextDay = getCurrentDay(anchor.getWorld(), state) + config.hordeSpawnDays;
            if (config.hordeSpawnVariation > 0) {
                nextDay += random.nextInt(config.hordeSpawnVariation + 1);
            }
            state.setNextHordeDay(nextDay);
        }

        if (owner != null) {
            owner.sendMessage(MM.deserialize(config.startMessage));
            playStartSound(owner);
        }

        int firstWaveAmount = rollNearbyScaledWaveAmount(anchor, owner, state);
        spawnWaveCore(anchor, owner, ownerId, state, firstWaveAmount, state.getActiveSpawnType());
        state.setWaveCooldownTicks(data.getIntervalTicks());
        return true;
    }

    public boolean stopHorde(Player player, boolean completedNaturally) {
        HordePlayerState state = states.get(player.getUniqueId());
        if (state == null || !state.isActive()) return false;
        return stopHordeCore(player, state, completedNaturally);
    }

    private boolean stopHordeCore(@Nullable Player player, HordePlayerState state, boolean completedNaturally) {
        if (!state.isActive()) return false;

        state.setActive(false);
        state.setRemainingTicks(0);
        state.setWaveCooldownTicks(0);

        if (player != null) {
            player.sendMessage(MM.deserialize(config.endMessage));
            Bukkit.getPluginManager().callEvent(new HordeEndEvent(player, completedNaturally));
            if (completedNaturally && !config.rewardCommands.isEmpty()) {
                dispatchRewardCommands(player, getCurrentDay(player, state));
            }
        }
        return true;
    }

    public void resetHorde(Player player) {
        stopHorde(player, false);
        HordePlayerState state = getOrCreateState(player);
        state.setNaturalHordeCount(0);
        state.setPlaytimeTicks(0);
        state.setNextHordeDay(config.spawnFirstDay ? 0 : config.hordeSpawnDays);
        state.getActiveEntities().clear();
    }

    private void tickActiveHordeCore(Location anchor, @Nullable Player owner, HordePlayerState state, int intervalTicks) {
        state.getActiveEntities().removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            return e == null || e.isDead() || !e.isValid();
        });

        int nextRemaining = state.getRemainingTicks() - intervalTicks;
        if (nextRemaining <= 0) {
            stopHordeCore(owner, state, true);
            return;
        }
        state.setRemainingTicks(nextRemaining);

        int nextWave = state.getWaveCooldownTicks() - intervalTicks;
        if (nextWave <= 0) {
            int waveAmount = rollNearbyScaledWaveAmount(anchor, owner, state);
            spawnWaveCore(anchor, owner, state.getPlayerId(), state, waveAmount, state.getActiveSpawnType());
            state.setWaveCooldownTicks(config.hordeSpawnInterval);
        } else {
            state.setWaveCooldownTicks(nextWave);
        }
    }

    /**
     * Rolls 5-10 zombies per nearby player within multiplayer-radius (default 64 blocks).
     * 1 solo player -> 5..10 zombies every 5 seconds.
     * 2 players together (<= 64 blocks) -> 10..20 zombies total every 5 seconds.
     */
    private int rollNearbyScaledWaveAmount(Location anchor, @Nullable Player owner, HordePlayerState state) {
        int playerCount = 1;
        World world = (owner != null) ? owner.getWorld() : (anchor != null ? anchor.getWorld() : null);
        Location center = (owner != null) ? owner.getLocation() : anchor;
        double maxRadius = config.hordeMultiplayerRadius > 0.0 ? config.hordeMultiplayerRadius : 64.0;
        double maxRadiusSq = maxRadius * maxRadius;

        if (world != null && center != null) {
            for (Player other : world.getPlayers()) {
                if (other.getUniqueId().equals(state.getPlayerId())) {
                    continue;
                }
                if (!other.isOnline() || other.isDead()) {
                    continue;
                }
                if (other.getGameMode() == GameMode.CREATIVE || other.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (other.getLocation().distanceSquared(center) <= maxRadiusSq) {
                    HordePlayerState otherState = states.get(other.getUniqueId());
                    if (otherState == null || !otherState.isActive()) {
                        playerCount++;
                    }
                }
            }
        }

        int total = 0;
        int span = (MAX_ZOMBIES_PER_PLAYER - MIN_ZOMBIES_PER_PLAYER) + 1;
        for (int i = 0; i < playerCount; i++) {
            total += MIN_ZOMBIES_PER_PLAYER + random.nextInt(span);
        }
        return Math.min(total, config.hordeSpawnMax);
    }

    public int spawnWave(Player player, int requestedCount, @Nullable String tableId, @Nullable String spawnType) {
        HordePlayerState state = getOrCreateState(player);
        String resolvedSpawnType = spawnType != null ? spawnType : "avoid_fluids";
        return spawnWaveCore(player.getLocation(), player, player.getUniqueId(), state, requestedCount, resolvedSpawnType);
    }

    public int spawnWaveCore(
            Location anchor,
            @Nullable Player owner,
            UUID ownerId,
            HordePlayerState state,
            int count,
            String spawnType
    ) {
        state.getActiveEntities().removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            return e == null || e.isDead() || !e.isValid();
        });

        int effectiveRequested = count;
        if (owner != null) {
            HordeStartWaveEvent waveEvent = new HordeStartWaveEvent(owner, count);
            Bukkit.getPluginManager().callEvent(waveEvent);
            if (waveEvent.isCancelled()) return 0;
            effectiveRequested = waveEvent.getCount();
        }

        int perPlayerRoom = Math.max(0, config.hordeSpawnMax - state.getActiveEntities().size());
        int globalCountAtStart = countGlobalHordeZombies();
        int globalRoom = Math.max(0, config.globalZombieCap - globalCountAtStart);
        int targetCount = Math.min(effectiveRequested, Math.min(perPlayerRoom, globalRoom));

        String ownerLabel = owner != null ? owner.getName() : state.getLastKnownName();
        if (targetCount < effectiveRequested && config.debug) {
            plugin.getLogger().info(String.format(
                    "Wave for %s reduced from %d to %d (playerRoom=%d, globalRoom=%d).",
                    ownerLabel, effectiveRequested, targetCount, perPlayerRoom, globalRoom));
        }
        if (targetCount <= 0) return 0;

        String resolvedSpawnType = (spawnType != null && !spawnType.isBlank()) ? spawnType : "avoid_fluids";
        int batchSize = Math.max(1, config.spawnBatchSize);
        int batchDelay = Math.max(1, config.spawnBatchDelayTicks);
        int totalBatches = (targetCount + batchSize - 1) / batchSize;

        AtomicInteger spawnedCounter = new AtomicInteger(0);
        AtomicInteger failedCounter = new AtomicInteger(0);

        for (int b = 0; b < totalBatches; b++) {
            final int batchIndex = b;
            final int countInBatch = Math.min(batchSize, targetCount - b * batchSize);
            long delay = (long) b * batchDelay;

            Runnable batchTask = () -> {
                if (owner != null && (!owner.isOnline() || owner.isDead())) return;
                Location waveOrigin = owner != null ? owner.getLocation() : anchor;
                int batchGlobalCount = countGlobalHordeZombies();

                for (int i = 0; i < countInBatch; i++) {
                    if (state.getActiveEntities().size() >= config.hordeSpawnMax
                            || batchGlobalCount >= config.globalZombieCap) {
                        break;
                    }
                    Location loc = findValidSpawnLocation(waveOrigin, resolvedSpawnType);
                    if (loc == null) {
                        failedCounter.incrementAndGet();
                        continue;
                    }
                    Location finalLoc = loc;
                    if (owner != null) {
                        HordeFindSpawnPosEvent posEvent = new HordeFindSpawnPosEvent(owner, loc);
                        Bukkit.getPluginManager().callEvent(posEvent);
                        finalLoc = posEvent.getSpawnLocation();
                    }
                    if (finalLoc == null || finalLoc.getWorld() == null) {
                        failedCounter.incrementAndGet();
                        continue;
                    }
                    Zombie spawned = spawnHordeZombieAt(finalLoc, ownerId, owner);
                    if (spawned != null) {
                        state.getActiveEntities().add(spawned.getUniqueId());
                        pathfindingQueue.add(spawned.getUniqueId());
                        batchGlobalCount++;
                        spawnedCounter.incrementAndGet();
                    } else {
                        failedCounter.incrementAndGet();
                    }
                }
                if (batchIndex == totalBatches - 1 && failedCounter.get() > 0) {
                    plugin.getLogger().warning(String.format(
                            "Horde wave for %s: spawned %d/%d zombies (%d failed to find valid spawn location after %d checks).",
                            ownerLabel, spawnedCounter.get(), targetCount, failedCounter.get(), config.hordeSpawnChecks));
                }
            };

            if (delay == 0L) {
                batchTask.run();
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, batchTask, delay);
            }
        }
        return targetCount;
    }

    public @Nullable Zombie spawnHordeZombieAt(Location loc, @Nullable UUID ownerId, @Nullable Player ownerPlayer) {
        if (loc.getWorld() == null) return null;

        Zombie zombie = loc.getWorld().spawn(
                loc,
                Zombie.class,
                CreatureSpawnEvent.SpawnReason.CUSTOM,
                false,
                z -> {}
        );

        if (config.hordeZombieNoBaby) {
            zombie.setAdult();
        }
        Entity vehicle = zombie.getVehicle();
        if (vehicle != null) {
            zombie.leaveVehicle();
            vehicle.remove();
        }
        if (!zombie.getPassengers().isEmpty()) {
            List<Entity> passengers = new ArrayList<>(zombie.getPassengers());
            zombie.eject();
            for (Entity p : passengers) {
                p.remove();
            }
        }

        if ("none".equalsIgnoreCase(config.hordeZombieEquipment)) {
            EntityEquipment eq = zombie.getEquipment();
            if (eq != null) {
                eq.clear();
                eq.setArmorContents(new ItemStack[4]);
                eq.setItemInMainHand(null);
                eq.setItemInOffHand(null);
            }
        }

        applyCrazyZombieAttributes(zombie);

        PersistentDataContainer pdc = zombie.getPersistentDataContainer();
        pdc.set(keys.hordeZombieTag, PersistentDataType.BYTE, (byte) 1);
        if (ownerId != null) {
            pdc.set(keys.hordeOwner, PersistentDataType.STRING, ownerId.toString());
        }
        zombie.setRemoveWhenFarAway(true);

        if (ownerPlayer != null) {
            HordeSpawnEntityEvent spawnEvent = new HordeSpawnEntityEvent(ownerPlayer, zombie);
            Bukkit.getPluginManager().callEvent(spawnEvent);
            if (spawnEvent.isCancelled()) {
                zombie.remove();
                return null;
            }
            zombie.setTarget(ownerPlayer);
            zombie.getPathfinder().moveTo(ownerPlayer, 1.0);
        }

        return zombie;
    }

    public void applyCrazyZombieAttributes(Zombie zombie) {
        zombie.setAggressive(true);
        zombie.setShouldBurnInDay(!config.hordeZombieNoSunburn);
        zombie.setCanBreakDoors(config.hordeZombieBreakDoors);
        zombie.getPathfinder().setCanOpenDoors(config.hordeZombieBreakDoors);

        AttributeInstance speed = zombie.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(keys.hordeSpeedModifier);
            double delta = config.hordeZombieSpeedMultiplier - 1.0;
            if (Math.abs(delta) > 0.0001) {
                speed.addModifier(new AttributeModifier(
                        keys.hordeSpeedModifier,
                        delta,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1
                ));
            }
        }

        AttributeInstance follow = zombie.getAttribute(Attribute.FOLLOW_RANGE);
        if (follow != null) {
            double desiredFollow = Math.max(config.hordeZombieFollowRange, config.hordeSpawnDistance + 25.0);
            follow.setBaseValue(desiredFollow);
        }

        if (config.hordeZombieNoReinforcements) {
            AttributeInstance reinf = zombie.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
            if (reinf != null) {
                reinf.setBaseValue(0.0);
            }
        }

        AttributeInstance kb = zombie.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.setBaseValue(config.hordeZombieKnockbackResistance);
        }

        AttributeInstance maxHp = zombie.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null && Math.abs(config.hordeZombieHealthMultiplier - 1.0) > 0.0001) {
            double scaledHp = Math.max(1.0, maxHp.getBaseValue() * config.hordeZombieHealthMultiplier);
            maxHp.setBaseValue(scaledHp);
            zombie.setHealth(scaledHp);
        }
    }

    public @Nullable Location findValidSpawnLocation(Location origin, String spawnType) {
        World world = origin.getWorld();
        if (world == null) return null;
        double dist = config.hordeSpawnDistance;

        for (int attempt = 0; attempt < config.hordeSpawnChecks; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = dist * (0.65 + 0.35 * random.nextDouble());
            int x = (int) Math.round(origin.getX() + Math.cos(angle) * r);
            int z = (int) Math.round(origin.getZ() + Math.sin(angle) * r);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

            int y = world.getHighestBlockYAt(x, z) + 1;
            Block feet = world.getBlockAt(x, y, z);
            Block below = world.getBlockAt(x, y - 1, z);
            Block head = world.getBlockAt(x, y + 1, z);

            boolean valid = switch (spawnType.toLowerCase(Locale.ROOT)) {
                case "prefer_water" -> feet.getType() == Material.WATER || (!feet.isSolid() && !head.isSolid());
                case "prefer_lava" -> feet.getType() == Material.LAVA || (!feet.isSolid() && !head.isSolid());
                case "ignore_fluids" -> !feet.isSolid() && !head.isSolid();
                case "ignore_water" -> feet.getType() != Material.LAVA && !feet.isSolid() && !head.isSolid();
                default -> !feet.isLiquid() && !below.isLiquid() && below.isSolid() && !feet.isSolid() && !head.isSolid();
            };

            if (valid) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    public int getCurrentDay(Player player, HordePlayerState state) {
        return getCurrentDay(player.getWorld(), state);
    }

    public int getCurrentDay(World world, HordePlayerState state) {
        long ticks = config.hordeEventByPlayerTime ? state.getPlaytimeTicks() : world.getFullTime();
        return (int) (ticks / config.dayLength);
    }

    private void playStartSound(Player player) {
        String raw = config.startSound == null || config.startSound.isBlank()
                ? "minecraft:entity.wolf.howl"
                : config.startSound.trim().toLowerCase(Locale.ROOT);
        if (!raw.contains(":")) {
            raw = "minecraft:" + raw.replace('_', '.');
        }
        try {
            Sound sound = Sound.sound(Key.key(raw), Sound.Source.HOSTILE, 1.0f, 0.8f);
            player.playSound(sound);
        } catch (Exception ignored) {
            Sound fallback = Sound.sound(Key.key("minecraft", "entity.wolf.howl"), Sound.Source.HOSTILE, 1.0f, 0.8f);
            player.playSound(fallback);
        }
    }

    private void dispatchRewardCommands(Player player, int day) {
        String safeName = player.getName();
        String safeWorld = player.getWorld().getName();
        for (String template : config.rewardCommands) {
            if (template == null || template.isBlank()) continue;
            String cmd = template
                    .replace("%player%", safeName)
                    .replace("%world%", safeWorld)
                    .replace("%day%", String.valueOf(day));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    public void loadData() {
        states.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = yml.getConfigurationSection("players");
        if (sec == null) return;
        for (String uuidStr : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(uuidStr);
                ConfigurationSection ps = sec.getConfigurationSection(uuidStr);
                if (ps == null) continue;
                HordePlayerState st = new HordePlayerState(
                        id,
                        ps.getString("name", "unknown"),
                        ps.getInt("next-horde-day", config.hordeSpawnDays)
                );
                st.setPlaytimeTicks(ps.getLong("playtime-ticks", 0));
                st.setNaturalHordeCount(ps.getInt("natural-horde-count", 0));
                st.setActive(ps.getBoolean("active", false));
                st.setRemainingTicks(ps.getInt("remaining-ticks", 0));
                st.setWaveCooldownTicks(ps.getInt("wave-cooldown-ticks", 0));
                st.setActiveTable("hordes:default");
                st.setActiveSpawnType(ps.getString("active-spawn-type", "avoid_fluids"));
                st.setCurrentWaveAmount(ps.getInt("current-wave-amount", config.spawnAmount));
                states.put(id, st);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid UUID in horde-data.yml: " + uuidStr);
            }
        }
    }

    public void saveData() {
        YamlConfiguration yml = new YamlConfiguration();
        for (HordePlayerState st : states.values()) {
            String base = "players." + st.getPlayerId();
            yml.set(base + ".name", st.getLastKnownName());
            yml.set(base + ".playtime-ticks", st.getPlaytimeTicks());
            yml.set(base + ".natural-horde-count", st.getNaturalHordeCount());
            yml.set(base + ".next-horde-day", st.getNextHordeDay());
            yml.set(base + ".active", st.isActive());
            yml.set(base + ".remaining-ticks", st.getRemainingTicks());
            yml.set(base + ".wave-cooldown-ticks", st.getWaveCooldownTicks());
            yml.set(base + ".active-table", st.getActiveTable());
            yml.set(base + ".active-spawn-type", st.getActiveSpawnType());
            yml.set(base + ".current-wave-amount", st.getCurrentWaveAmount());
        }
        try {
            yml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save horde-data.yml: " + e.getMessage());
        }
    }
}
