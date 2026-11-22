package net.neon.testkit;

import io.papermc.paper.block.bed.BedEnterAction;
import net.neon.hordes.HordesPaperPlugin;
import net.neon.hordes.config.HordesConfig;
import net.neon.hordes.core.HordeManager;
import net.neon.hordes.core.HordePlayerState;
import net.neon.hordes.core.HordeSpawnData;
import net.neon.hordes.core.InfectionManager;
import net.neon.hordes.core.ZombiePlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements acceptance tests AT-01 through AT-23 per PRD v2.1, PRD v3 Section 7, and BUG-1..BUG-8.
 * All behavioral and load tests invoke real plugin logic on the main server thread.
 */
public final class AcceptanceTests {

    private static final int CHUNK_HOLD_RADIUS = 7; // 15x15 chunks (radius 7 around center)

    public record TestResult(String id, boolean passed, boolean informational, boolean skipped, String detail) {
        public TestResult(String id, boolean passed, String detail) {
            this(id, passed, false, false, detail);
        }

        public static TestResult info(String id, String detail) {
            return new TestResult(id, false, true, false, detail);
        }

        public static TestResult skipped(String id, String detail) {
            return new TestResult(id, false, true, true, detail);
        }
    }

    public static final List<String> ALL_IDS = List.of(
            "AT-01", "AT-02", "AT-03", "AT-04", "AT-05", "AT-06", "AT-07",
            "AT-08", "AT-09", "AT-10", "AT-11", "AT-12", "AT-13", "AT-14",
            "AT-15", "AT-16", "AT-17", "AT-18", "AT-19", "AT-20", "AT-21",
            "AT-22", "AT-23"
    );

    private final NeonTestkitPlugin kit;
    private final HordesPaperPlugin plugin;

    public AcceptanceTests(NeonTestkitPlugin kit, HordesPaperPlugin plugin) {
        this.kit = kit;
        this.plugin = plugin;
    }

    public CompletableFuture<List<TestResult>> runAllAsync() {
        List<TestResult> results = new ArrayList<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String id : ALL_IDS) {
            chain = chain.thenCompose(ignored -> runSingleAsync(id).thenAccept(results::add));
        }
        return chain.thenApply(ignored -> results);
    }

    public CompletableFuture<TestResult> runSingleAsync(String rawId) {
        String id = rawId.toUpperCase(Locale.ROOT);
        try {
            return switch (id) {
                case "AT-01" -> CompletableFuture.completedFuture(testAt01PluginLoad());
                case "AT-02" -> testAt02HordeNightBehaviorAsync();
                case "AT-03" -> CompletableFuture.completedFuture(testAt03SpawnTableBehavior());
                case "AT-04" -> CompletableFuture.completedFuture(testAt04InfectionAndCure());
                case "AT-05" -> CompletableFuture.completedFuture(testAt05ZombiePlayerAndItems());
                case "AT-06" -> CompletableFuture.completedFuture(testAt06Commands());
                case "AT-07" -> CompletableFuture.completedFuture(testAt07ConfigReload());
                case "AT-08" -> testAt08Load100MobsAsync();
                case "AT-09" -> CompletableFuture.completedFuture(testAt09PersistenceRestart());
                case "AT-10" -> CompletableFuture.completedFuture(testAt10SpawnScaling());
                case "AT-11" -> CompletableFuture.completedFuture(testAt11InfectionDurationScaling());
                case "AT-12" -> CompletableFuture.completedFuture(testAt12SleepBlockingBehavior());
                case "AT-13" -> CompletableFuture.completedFuture(testAt13EntityConversions());
                case "AT-14" -> CompletableFuture.completedFuture(testAt14NonVanillaDefaultsBehavior());
                case "AT-15" -> testAt15Wave25BatchedAsync();
                case "AT-16" -> CompletableFuture.completedFuture(testAt16ZombieOnlyNoBabyNoOtherMob());
                case "AT-17" -> CompletableFuture.completedFuture(testAt17CrazyZombieAttributes());
                case "AT-18" -> testAt18NoSunburn200TicksAsync();
                case "AT-19" -> testAt19Chase60BlocksAsync();
                case "AT-20" -> testAt20AliveCap200AndGlobalCap400Async();
                case "AT-21" -> testAt21Load200ZombiesInformationalAsync();
                case "AT-22" -> CompletableFuture.completedFuture(testAt22ReloadSpeedMultiplier());
                case "AT-23" -> testAt23LockedTableOverrideAsync();
                default -> CompletableFuture.completedFuture(new TestResult(id, false, "Unknown test ID"));
            };
        } catch (Throwable t) {
            return CompletableFuture.completedFuture(
                    new TestResult(id, false, "Exception: " + t.getClass().getSimpleName() + ": " + t.getMessage()));
        }
    }

    private World mainWorld() {
        return Bukkit.getWorlds().getFirst();
    }

    private Location safeTestLoc() {
        World w = mainWorld();
        int y = w.getHighestBlockYAt(0, 0) + 1;
        return new Location(w, 0.5, y, 0.5);
    }

    /**
     * BUG-2: Loads and holds a 15x15 chunk area via plugin chunk tickets and force-loading
     * so tests requiring 60-75 block spawn/chase distance have active chunks without online players.
     */
    private void holdChunkArea(World world, Location center) {
        int baseCx = center.getBlockX() >> 4;
        int baseCz = center.getBlockZ() >> 4;
        for (int dx = -CHUNK_HOLD_RADIUS; dx <= CHUNK_HOLD_RADIUS; dx++) {
            for (int dz = -CHUNK_HOLD_RADIUS; dz <= CHUNK_HOLD_RADIUS; dz++) {
                int cx = baseCx + dx;
                int cz = baseCz + dz;
                world.getChunkAt(cx, cz).load();
                world.addPluginChunkTicket(cx, cz, kit);
                world.setChunkForceLoaded(cx, cz, true);
            }
        }
    }

    private void releaseChunkArea(World world, Location center) {
        int baseCx = center.getBlockX() >> 4;
        int baseCz = center.getBlockZ() >> 4;
        for (int dx = -CHUNK_HOLD_RADIUS; dx <= CHUNK_HOLD_RADIUS; dx++) {
            for (int dz = -CHUNK_HOLD_RADIUS; dz <= CHUNK_HOLD_RADIUS; dz++) {
                int cx = baseCx + dx;
                int cz = baseCz + dz;
                world.removePluginChunkTicket(cx, cz, kit);
                world.setChunkForceLoaded(cx, cz, false);
            }
        }
    }

    private void cleanupStateEntities(HordePlayerState state) {
        for (UUID id : new ArrayList<>(state.getActiveEntities())) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid()) e.remove();
        }
        state.getActiveEntities().clear();
    }

    private TestResult testAt01PluginLoad() {
        boolean ok = plugin.isEnabled()
                && plugin.getHordesConfig() != null
                && plugin.getHordeManager() != null
                && plugin.getInfectionManager() != null
                && plugin.getZombiePlayerManager() != null;
        return new TestResult("AT-01", ok, ok ? "HordesPaper enabled cleanly with all managers initialized" : "Plugin or managers missing");
    }

    /** BUG-1: Runs real HordeManager.tickState at hordeStartTime and verifies state.isActive() transitions. */
    private CompletableFuture<TestResult> testAt02HordeNightBehaviorAsync() {
        CompletableFuture<TestResult> future = new CompletableFuture<>();
        HordesConfig cfg = plugin.getHordesConfig();
        HordeManager hm = plugin.getHordeManager();
        World w = mainWorld();
        Location origin = safeTestLoc();
        long prevTime = w.getTime();
        holdChunkArea(w, origin);

        UUID testId = UUID.randomUUID();
        HordePlayerState state = new HordePlayerState(testId, "At02Tester", cfg.hordeSpawnDays);
        state.setPlaytimeTicks((long) cfg.hordeSpawnDays * cfg.dayLength);
        hm.registerState(state);

        // Before start window: must remain inactive
        w.setTime(6000L);
        hm.tickState(w, origin, null, state, 20);
        boolean inactiveAtNoon = !state.isActive();

        // At hordeStartTime (18000): HordeManager.tickState must trigger natural horde start
        w.setTime(cfg.hordeStartTime);
        hm.tickState(w, origin, null, state, 20);
        boolean activatedAtNight = state.isActive();
        int initialRemaining = state.getRemainingTicks();

        // Wait 30 ticks for any scheduled first-wave batches to finish before cleanup
        Bukkit.getScheduler().runTaskLater(kit, () -> {
            try {
                // Advance remainingTicks to completion and verify tickState transitions active -> false
                state.setRemainingTicks(20);
                hm.tickState(w, origin, null, state, 20);
                boolean deactivatedAtEnd = !state.isActive();

                boolean ok = inactiveAtNoon && activatedAtNight && initialRemaining == cfg.hordeSpawnDuration && deactivatedAtEnd;
                future.complete(new TestResult("AT-02", ok,
                        "inactiveAtNoon=" + inactiveAtNoon + ", activatedAtNight=" + activatedAtNight
                                + ", duration=" + initialRemaining + ", deactivatedAtEnd=" + deactivatedAtEnd));
            } finally {
                cleanupStateEntities(state);
                hm.removeState(testId);
                w.setTime(prevTime);
                releaseChunkArea(w, origin);
            }
        }, 30L);
        return future;
    }

    /** AT-03: verifies spawn location finder and actual ZOMBIE spawning behavior. */
    private TestResult testAt03SpawnTableBehavior() {
        HordesConfig cfg = plugin.getHordesConfig();
        HordeManager hm = plugin.getHordeManager();
        World w = mainWorld();
        Location origin = safeTestLoc();
        holdChunkArea(w, origin);
        Zombie spawned = null;
        try {
            Location found = hm.findValidSpawnLocation(origin, "avoid_fluids");
            spawned = found != null ? hm.spawnHordeZombieAt(found, null, null) : null;
            double dist = found != null ? origin.distance(found) : -1.0;
            boolean distOk = dist >= cfg.hordeSpawnDistance * 0.60 && dist <= cfg.hordeSpawnDistance * 1.05;
            boolean spawnedOk = spawned != null && spawned.getType() == EntityType.ZOMBIE && hm.isHordeZombie(spawned);
            var defEntries = cfg.spawnTables.getOrDefault("hordes:default", List.of());
            boolean tableOk = !defEntries.isEmpty() && defEntries.stream().allMatch(e -> e.entityType() == EntityType.ZOMBIE);
            boolean ok = distOk && spawnedOk && tableOk;
            return new TestResult("AT-03", ok, String.format("spawned=%s, dist=%.1f (target %.1f), tableZombieOnly=%s",
                    spawnedOk, dist, cfg.hordeSpawnDistance, tableOk));
        } finally {
            if (spawned != null && spawned.isValid()) spawned.remove();
            releaseChunkArea(w, origin);
        }
    }

    private TestResult testAt04InfectionAndCure() {
        InfectionManager im = plugin.getInfectionManager();
        Location loc = safeTestLoc();
        Villager villager = (Villager) mainWorld().spawnEntity(loc, EntityType.VILLAGER);
        try {
            im.applyInfection(villager, 0);
            boolean infected = im.isInfected(villager) && im.getInfectedStage(villager) == 0;
            boolean cured = im.cureInfection(villager, true);
            boolean afterCure = !im.isInfected(villager) && im.getInfectionCount(villager) == 1;
            im.applyImmunityIfApplicable(villager, Material.ENCHANTED_GOLDEN_APPLE);
            boolean immune = im.hasImmunity(villager) && im.getImmunityRemainingTicks(villager) == 3000;
            boolean ok = infected && cured && afterCure && immune && im.isCureItem(Material.GOLDEN_APPLE);
            return new TestResult("AT-04", ok, "infected=" + infected + ", cured=" + afterCure + ", immune3000t=" + immune);
        } finally {
            villager.remove();
        }
    }

    private TestResult testAt05ZombiePlayerAndItems() {
        ZombiePlayerManager zpm = plugin.getZombiePlayerManager();
        Location loc = safeTestLoc();
        List<ItemStack> drops = List.of(ItemStack.of(Material.DIAMOND_SWORD, 1), ItemStack.of(Material.GOLDEN_APPLE, 2));
        Zombie zp = zpm.spawnZombiePlayer("NeonTester", null, loc, ZombiePlayerManager.Variant.ZOMBIE, drops);
        try {
            boolean tagged = zp != null && zpm.isZombiePlayer(zp);
            List<ItemStack> extracted = zp != null ? zpm.extractStoredItems(zp) : List.of();
            boolean itemsOk = extracted.size() == 2;
            boolean ok = tagged && itemsOk;
            return new TestResult("AT-05", ok, "zombieTagged=" + tagged + ", storedItems=" + extracted.size());
        } finally {
            if (zp != null) zp.remove();
        }
    }

    private TestResult testAt06Commands() {
        boolean debugDispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hordes debug");
        boolean listDispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hordes listEntities");
        boolean reloadDispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hordes reload");
        boolean ok = debugDispatched && listDispatched && reloadDispatched;
        return new TestResult("AT-06", ok, "debug=" + debugDispatched + ", listEntities=" + listDispatched + ", reload=" + reloadDispatched);
    }

    private TestResult testAt07ConfigReload() {
        plugin.getHordesConfig().reload();
        boolean ok = plugin.getHordesConfig().spawnAmount > 0 && !plugin.getHordesConfig().spawnTables.isEmpty();
        return new TestResult("AT-07", ok, "Reloaded without exception; tables=" + plugin.getHordesConfig().spawnTables.size());
    }

    /** BUG-8: Informational load test — spawns 100 mobs, waits 100 ticks active, records MSPT & TPS. */
    private CompletableFuture<TestResult> testAt08Load100MobsAsync() {
        CompletableFuture<TestResult> future = new CompletableFuture<>();
        World w = mainWorld();
        Location loc = safeTestLoc();
        holdChunkArea(w, loc);
        List<Entity> spawned = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            spawned.add(w.spawnEntity(loc, EntityType.ZOMBIE));
        }
        Bukkit.getScheduler().runTaskLater(kit, () -> {
            try {
                long alive = spawned.stream().filter(e -> e.isValid() && !e.isDead()).count();
                double mspt = Bukkit.getAverageTickTime();
                double[] tps = Bukkit.getTPS();
                double tps1m = tps.length > 0 ? tps[0] : 20.0;
                future.complete(TestResult.info("AT-08",
                        String.format("INFO: spawned=%d (alive=%d after 100t), MSPT=%.2fms, TPS_1m=%.2f",
                                spawned.size(), alive, mspt, tps1m)));
            } finally {
                for (Entity e : spawned) if (e.isValid()) e.remove();
                releaseChunkArea(w, loc);
            }
        }, 100L);
        return future;
    }

    /** BUG-1: Saves real active state, clears map in memory, reloads from disk, and compares exact fields. */
    private TestResult testAt09PersistenceRestart() {
        HordeManager hm = plugin.getHordeManager();
        UUID testId = UUID.randomUUID();
        HordePlayerState original = new HordePlayerState(testId, "At09Persist", 17);
        original.setActive(true);
        original.setRemainingTicks(4321);
        original.setNaturalHordeCount(3);
        original.setPlaytimeTicks(250000L);
        original.setWaveCooldownTicks(140);
        try {
            hm.registerState(original);
            hm.saveData();
            hm.clearStatesInMemory();
            boolean cleared = hm.getState(testId) == null;
            hm.loadData();
            HordePlayerState reloaded = hm.getState(testId);
            boolean ok = cleared
                    && reloaded != null
                    && reloaded.isActive()
                    && reloaded.getRemainingTicks() == 4321
                    && reloaded.getNextHordeDay() == 17
                    && reloaded.getNaturalHordeCount() == 3;
            return new TestResult("AT-09", ok,
                    reloaded == null
                            ? "Reloaded state was null"
                            : String.format("cleared=%s, active=%s, remaining=%d (exp 4321), nextDay=%d (exp 17), naturalCount=%d (exp 3)",
                            cleared, reloaded.isActive(), reloaded.getRemainingTicks(), reloaded.getNextHordeDay(), reloaded.getNaturalHordeCount()));
        } finally {
            hm.removeState(testId);
            hm.saveData();
        }
    }

    /** BUG-1: Calls real HordeManager.buildSpawnDataCore instead of recalculating formulas manually. */
    private TestResult testAt10SpawnScaling() {
        HordeManager hm = plugin.getHordeManager();
        Location origin = safeTestLoc();
        HordePlayerState st0 = new HordePlayerState(UUID.randomUUID(), "At10_0", 10);
        st0.setNaturalHordeCount(0);
        HordeSpawnData d0 = hm.buildSpawnDataCore(origin, null, st0, null, true, 0);

        HordePlayerState st1 = new HordePlayerState(UUID.randomUUID(), "At10_1", 10);
        st1.setNaturalHordeCount(1);
        HordeSpawnData d1 = hm.buildSpawnDataCore(origin, null, st1, null, true, 0);

        HordePlayerState st100 = new HordePlayerState(UUID.randomUUID(), "At10_100", 10);
        st100.setNaturalHordeCount(100);
        HordeSpawnData dCap = hm.buildSpawnDataCore(origin, null, st100, null, true, 0);

        HordeSpawnData dMp = hm.buildSpawnDataCore(origin, null, st0, null, true, 1);

        boolean ok = d0.getSpawnAmount() == 25
                && d1.getSpawnAmount() == 26
                && dCap.getSpawnAmount() == 200
                && dMp.getSpawnAmount() == 20;
        return new TestResult("AT-10", ok,
                "buildSpawnDataCore -> h0=" + d0.getSpawnAmount()
                        + ", h1=" + d1.getSpawnAmount()
                        + ", cap=" + dCap.getSpawnAmount()
                        + ", mpScaled=" + dMp.getSpawnAmount());
    }

    private TestResult testAt11InfectionDurationScaling() {
        InfectionManager im = plugin.getInfectionManager();
        Villager v = (Villager) mainWorld().spawnEntity(safeTestLoc(), EntityType.VILLAGER);
        try {
            int d0 = im.getStageDurationTicks(v);
            im.applyInfection(v, 0);
            im.cureInfection(v, true);
            int d1 = im.getStageDurationTicks(v);
            im.resetInfectionCount(v);
            int dReset = im.getStageDurationTicks(v);
            boolean ok = d0 == 6000 && d1 == 5700 && dReset == 6000 && (InfectionManager.MAX_STAGE_INDEX + 1) == 4;
            return new TestResult("AT-11", ok, "stages=4, d0=" + d0 + ", d1=" + d1 + ", dReset=" + dReset);
        } finally {
            v.remove();
        }
    }

    /** BUG-1: Fires real PlayerBedEnterEvent through PluginManager to HordeListener with active & inactive horde state. */
    private TestResult testAt12SleepBlockingBehavior() {
        HordeManager hm = plugin.getHordeManager();
        UUID testId = UUID.randomUUID();
        HordePlayerState state = new HordePlayerState(testId, "At12Sleeper", 10);
        state.setActive(true);
        state.setRemainingTicks(1000);
        hm.registerState(state);

        Block bedBlock = safeTestLoc().getBlock();
        Player proxyPlayer = createTestPlayerProxy(testId, "At12Sleeper");
        BedEnterAction proxyAction = createTestBedActionProxy();

        try {
            PlayerBedEnterEvent duringHorde = new PlayerBedEnterEvent(
                    proxyPlayer, bedBlock, PlayerBedEnterEvent.BedEnterResult.OK, proxyAction);
            Bukkit.getPluginManager().callEvent(duringHorde);
            boolean blockedDuringHorde = duringHorde.isCancelled();

            state.setActive(false);
            PlayerBedEnterEvent afterHorde = new PlayerBedEnterEvent(
                    proxyPlayer, bedBlock, PlayerBedEnterEvent.BedEnterResult.OK, proxyAction);
            Bukkit.getPluginManager().callEvent(afterHorde);
            boolean allowedWhenIdle = !afterHorde.isCancelled();

            boolean ok = blockedDuringHorde && allowedWhenIdle;
            return new TestResult("AT-12", ok,
                    "blockedDuringHorde=" + blockedDuringHorde + ", allowedWhenIdle=" + allowedWhenIdle);
        } finally {
            hm.removeState(testId);
        }
    }

    private Player createTestPlayerProxy(UUID uuid, String name) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String m = method.getName();
                    if ("getUniqueId".equals(m)) return uuid;
                    if ("getName".equals(m)) return name;
                    if ("equals".equals(m)) return proxy == args[0];
                    if ("hashCode".equals(m)) return uuid.hashCode();
                    if ("toString".equals(m)) return "TestPlayerProxy[" + name + "]";
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class) return 0;
                    if (ret == long.class) return 0L;
                    if (ret == double.class) return 0.0;
                    if (ret == float.class) return 0.0f;
                    return null;
                }
        );
    }

    private BedEnterAction createTestBedActionProxy() {
        return (BedEnterAction) Proxy.newProxyInstance(
                BedEnterAction.class.getClassLoader(),
                new Class<?>[]{BedEnterAction.class},
                (proxy, method, args) -> {
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class) return 0;
                    return null;
                }
        );
    }

    private TestResult testAt13EntityConversions() {
        InfectionManager im = plugin.getInfectionManager();
        Location loc = safeTestLoc();
        Villager vil = (Villager) mainWorld().spawnEntity(loc, EntityType.VILLAGER);
        Horse horse = (Horse) mainWorld().spawnEntity(loc, EntityType.HORSE);
        LivingEntity c1 = null;
        LivingEntity c2 = null;
        try {
            c1 = im.convertMob(vil);
            c2 = im.convertMob(horse);
            boolean ok = c1 instanceof ZombieVillager && c2 instanceof ZombieHorse;
            return new TestResult("AT-13", ok, "villager->" + (c1 != null ? c1.getType() : "null") + ", horse->" + (c2 != null ? c2.getType() : "null"));
        } finally {
            if (vil.isValid()) vil.remove();
            if (horse.isValid()) horse.remove();
            if (c1 != null && c1.isValid()) c1.remove();
            if (c2 != null && c2.isValid()) c2.remove();
        }
    }

    /** AT-14: tests actual EntityCombustEvent cancellation for zombie & skeleton. */
    private TestResult testAt14NonVanillaDefaultsBehavior() {
        HordesConfig cfg = plugin.getHordesConfig();
        Zombie z = (Zombie) mainWorld().spawnEntity(safeTestLoc(), EntityType.ZOMBIE);
        Skeleton s = (Skeleton) mainWorld().spawnEntity(safeTestLoc(), EntityType.SKELETON);
        try {
            EntityCombustEvent cz = new EntityCombustEvent(z, 8.0f);
            EntityCombustEvent cs = new EntityCombustEvent(s, 8.0f);
            Bukkit.getPluginManager().callEvent(cz);
            Bukkit.getPluginManager().callEvent(cs);
            boolean ok = !cfg.zombiesBurn && cz.isCancelled() && !cfg.skeletonsBurn && cs.isCancelled() && cfg.aggressiveZombieHorses;
            return new TestResult("AT-14", ok, "zombieCombustCancelled=" + cz.isCancelled() + ", skeletonCombustCancelled=" + cs.isCancelled());
        } finally {
            z.remove();
            s.remove();
        }
    }

    /**
     * BUG-1: Calls real HordeManager.spawnWaveCore and measures per-tick spawn distribution:
     * exactly 25 alive zombies, all spawned within <= 200 ticks, and <= 5 zombies in any 4-tick window.
     */
    private CompletableFuture<TestResult> testAt15Wave25BatchedAsync() {
        CompletableFuture<TestResult> future = new CompletableFuture<>();
        HordeManager hm = plugin.getHordeManager();
        HordesConfig cfg = plugin.getHordesConfig();
        World w = mainWorld();
        Location origin = safeTestLoc();
        holdChunkArea(w, origin);

        UUID ownerId = UUID.randomUUID();
        HordePlayerState state = new HordePlayerState(ownerId, "At15Tester", 10);
        state.setActive(true);
        state.setRemainingTicks(6000);
        hm.registerState(state);

        int[] spawnsPerTick = new int[205];
        Set<UUID> seen = new HashSet<>();
        int startTick = Bukkit.getCurrentTick();

        int scheduled = hm.spawnWaveCore(origin, null, ownerId, state, cfg.spawnAmount, "avoid_fluids");
        // Capture tick-0 batch spawned synchronously
        for (UUID id : state.getActiveEntities()) {
            if (seen.add(id)) {
                spawnsPerTick[0]++;
            }
        }

        BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(kit, () -> {
            int offset = Bukkit.getCurrentTick() - startTick;
            int bucket = Math.max(0, Math.min(offset, spawnsPerTick.length - 1));
            for (UUID id : state.getActiveEntities()) {
                if (seen.add(id)) {
                    spawnsPerTick[bucket]++;
                }
            }

            long aliveCount = state.getActiveEntities().stream()
                    .map(Bukkit::getEntity)
                    .filter(e -> e instanceof Zombie && e.isValid() && !e.isDead())
                    .count();

            if (aliveCount >= 25 || offset >= 200) {
                taskRef[0].cancel();
                int maxIn4Ticks = 0;
                for (int t = 0; t <= Math.min(offset, 200); t++) {
                    int windowSum = 0;
                    for (int k = 0; k < 4 && (t + k) < spawnsPerTick.length; k++) {
                        windowSum += spawnsPerTick[t + k];
                    }
                    if (windowSum > maxIn4Ticks) maxIn4Ticks = windowSum;
                }
                boolean ok = scheduled == 25 && aliveCount == 25 && offset <= 200 && maxIn4Ticks <= 5;
                cleanupStateEntities(state);
                hm.removeState(ownerId);
                releaseChunkArea(w, origin);
                future.complete(new TestResult("AT-15", ok,
                        String.format("scheduled=%d, alive=%d/25, elapsedTicks=%d (<=200), maxIn4Ticks=%d (<=5)",
                                scheduled, aliveCount, offset, maxIn4Ticks)));
            }
        }, 1L, 1L);

        return future;
    }

    /** BUG-6: Spawns 200 zombies repeatedly and verifies zero babies, zero Chickens/non-zombies within 3 blocks, and target-lock. */
    private TestResult testAt16ZombieOnlyNoBabyNoOtherMob() {
        HordeManager hm = plugin.getHordeManager();
        World w = mainWorld();
        Location origin = safeTestLoc();
        List<Zombie> spawned = new ArrayList<>(200);
        try {
            // Clear any pre-existing living entities within 3 blocks
            for (Entity existing : w.getNearbyEntities(origin, 3.0, 3.0, 3.0)) {
                if (existing instanceof LivingEntity && !(existing instanceof Player)) {
                    existing.remove();
                }
            }

            for (int i = 0; i < 200; i++) {
                Zombie z = hm.spawnHordeZombieAt(origin, null, null);
                if (z != null) spawned.add(z);
            }
            boolean allPlainAdult = spawned.size() == 200 && spawned.stream().allMatch(z ->
                    z.getType() == EntityType.ZOMBIE
                            && z.isAdult()
                            && !z.isInsideVehicle()
                            && z.getPassengers().isEmpty()
                            && hm.isHordeZombie(z));

            long nonZombieNearby = w.getNearbyEntities(origin, 3.0, 3.0, 3.0).stream()
                    .filter(e -> e instanceof LivingEntity && e.getType() != EntityType.ZOMBIE && !(e instanceof Player))
                    .count();

            Villager distraction = (Villager) w.spawnEntity(origin, EntityType.VILLAGER);
            EntityTargetEvent targetEvent = new EntityTargetEvent(spawned.getFirst(), distraction, EntityTargetEvent.TargetReason.CLOSEST_ENTITY);
            Bukkit.getPluginManager().callEvent(targetEvent);
            distraction.remove();
            boolean targetLockOk = targetEvent.isCancelled();

            boolean ok = allPlainAdult && nonZombieNearby == 0 && targetLockOk;
            return new TestResult("AT-16", ok,
                    "spawned=" + spawned.size() + "/200, allPlainAdult=" + allPlainAdult
                            + ", nonZombieWithin3b=" + nonZombieNearby + ", targetLockCancelledVillager=" + targetLockOk);
        } finally {
            for (Zombie z : spawned) if (z.isValid()) z.remove();
        }
    }

    /** AT-17 (PRD v3): Crazy zombie attributes (MOVEMENT_SPEED, FOLLOW_RANGE, SPAWN_REINFORCEMENTS = 0, KNOCKBACK_RESISTANCE). */
    private TestResult testAt17CrazyZombieAttributes() {
        HordeManager hm = plugin.getHordeManager();
        HordesConfig cfg = plugin.getHordesConfig();
        Zombie z = hm.spawnHordeZombieAt(safeTestLoc(), null, null);
        try {
            if (z == null) return new TestResult("AT-17", false, "Failed to spawn zombie");
            AttributeInstance spd = z.getAttribute(Attribute.MOVEMENT_SPEED);
            AttributeInstance follow = z.getAttribute(Attribute.FOLLOW_RANGE);
            AttributeInstance reinf = z.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
            AttributeInstance kb = z.getAttribute(Attribute.KNOCKBACK_RESISTANCE);

            double expectedSpd = spd != null ? spd.getBaseValue() * cfg.hordeZombieSpeedMultiplier : 0.0;
            double actualSpd = spd != null ? spd.getValue() : 0.0;
            boolean spdOk = Math.abs(actualSpd - expectedSpd) < 0.005;
            boolean followOk = follow != null && follow.getValue() >= cfg.hordeZombieFollowRange;
            boolean reinfOk = reinf != null && reinf.getBaseValue() == 0.0;
            boolean kbOk = kb != null && Math.abs(kb.getBaseValue() - cfg.hordeZombieKnockbackResistance) < 0.001;
            boolean doorsOk = z.canBreakDoors();

            boolean ok = spdOk && followOk && reinfOk && kbOk && doorsOk;
            return new TestResult("AT-17", ok, String.format("speed=%.4f (exp %.4f), follow=%.1f, reinf=%.1f, kb=%.2f, breakDoors=%s",
                    actualSpd, expectedSpd, follow != null ? follow.getValue() : 0, reinf != null ? reinf.getBaseValue() : -1,
                    kb != null ? kb.getBaseValue() : -1, doorsOk));
        } finally {
            if (z != null && z.isValid()) z.remove();
        }
    }

    /**
     * BUG-2: Holds 15x15 chunk tickets, checks whether zombie is actually ticking via getTicksLived() over 20 ticks.
     * If not ticking, reports INFO SKIPPED instead of false PASS/FAIL.
     */
    private CompletableFuture<TestResult> testAt18NoSunburn200TicksAsync() {
        CompletableFuture<TestResult> future = new CompletableFuture<>();
        World w = mainWorld();
        Location origin = safeTestLoc();
        long prevTime = w.getTime();
        holdChunkArea(w, origin);
        w.setTime(6000L); // Noon broad daylight

        Zombie z = plugin.getHordeManager().spawnHordeZombieAt(origin, null, null);
        if (z == null) {
            w.setTime(prevTime);
            releaseChunkArea(w, origin);
            future.complete(new TestResult("AT-18", false, "Failed to spawn zombie"));
            return future;
        }

        int initialTicksLived = z.getTicksLived();
        Bukkit.getScheduler().runTaskLater(kit, () -> {
            if (!z.isValid() || z.isDead()) {
                w.setTime(prevTime);
                releaseChunkArea(w, origin);
                future.complete(new TestResult("AT-18", false, "Zombie died/removed during initial 20-tick check"));
                return;
            }
            if (z.getTicksLived() <= initialTicksLived) {
                z.remove();
                w.setTime(prevTime);
                releaseChunkArea(w, origin);
                future.complete(TestResult.skipped("AT-18", "SKIPPED: tidak ada pemain/tick aktif di server uji"));
                return;
            }

            AtomicInteger burnedTicks = new AtomicInteger(z.getFireTicks() > 0 ? 1 : 0);
            AtomicInteger elapsed = new AtomicInteger(20);
            BukkitTask[] taskRef = new BukkitTask[1];
            taskRef[0] = Bukkit.getScheduler().runTaskTimer(kit, () -> {
                if (!z.isValid() || z.isDead()) {
                    taskRef[0].cancel();
                    w.setTime(prevTime);
                    releaseChunkArea(w, origin);
                    future.complete(new TestResult("AT-18", false, "Zombie died/removed during 200-tick sunburn test"));
                    return;
                }
                if (z.getFireTicks() > 0) {
                    burnedTicks.incrementAndGet();
                }
                if (elapsed.addAndGet(10) >= 200) {
                    taskRef[0].cancel();
                    boolean ok = burnedTicks.get() == 0;
                    int livedDelta = z.getTicksLived() - initialTicksLived;
                    z.remove();
                    w.setTime(prevTime);
                    releaseChunkArea(w, origin);
                    future.complete(new TestResult("AT-18", ok,
                            "burnedChecks=" + burnedTicks.get() + "/19 over 200 daylight ticks (ticksLivedDelta=" + livedDelta + ")"));
                }
            }, 10L, 10L);
        }, 20L);
        return future;
    }

    /**
     * BUG-2: Holds 15x15 chunk tickets, checks whether zombie is actually ticking via getTicksLived() over 20 ticks.
     * If not ticking, reports INFO SKIPPED instead of false PASS/FAIL.
     */
    private CompletableFuture<TestResult> testAt19Chase60BlocksAsync() {
        CompletableFuture<TestResult> future = new CompletableFuture<>();
        World w = mainWorld();
        Location targetLoc = safeTestLoc();
        holdChunkArea(w, targetLoc);
        Location spawnLoc = targetLoc.clone().add(60.0, 0.0, 0.0);

        Zombie z = plugin.getHordeManager().spawnHordeZombieAt(spawnLoc, null, null);
        if (z == null) {
            releaseChunkArea(w, targetLoc);
            future.complete(new TestResult("AT-19", false, "Failed to spawn zombie at 60 blocks"));
            return future;
        }
        z.getPathfinder().moveTo(targetLoc, 1.0);
        int initialTicksLived = z.getTicksLived();

        Bukkit.getScheduler().runTaskLater(kit, () -> {
            if (!z.isValid() || z.isDead()) {
                releaseChunkArea(w, targetLoc);
                future.complete(new TestResult("AT-19", false, "Zombie removed during initial 20-tick check"));
                return;
            }
            if (z.getTicksLived() <= initialTicksLived) {
                z.remove();
                releaseChunkArea(w, targetLoc);
                future.complete(TestResult.skipped("AT-19", "SKIPPED: tidak ada pemain/tick aktif di server uji"));
                return;
            }

            AtomicInteger ticks = new AtomicInteger(20);
            BukkitTask[] taskRef = new BukkitTask[1];
            taskRef[0] = Bukkit.getScheduler().runTaskTimer(kit, () -> {
                if (!z.isValid() || z.isDead()) {
                    taskRef[0].cancel();
                    releaseChunkArea(w, targetLoc);
                    future.complete(new TestResult("AT-19", false, "Zombie removed during chase"));
                    return;
                }
                double dist = z.getLocation().distance(targetLoc);
                if (dist <= 3.0) {
                    taskRef[0].cancel();
                    z.remove();
                    releaseChunkArea(w, targetLoc);
                    future.complete(new TestResult("AT-19", true,
                            String.format("Reached target (dist=%.2f <= 3.0) in %d ticks (<= 600)", dist, ticks.get())));
                    return;
                }
                if (!z.getPathfinder().hasPath()) {
                    z.getPathfinder().moveTo(targetLoc, 1.0);
                }
                if (ticks.addAndGet(10) >= 600) {
                    taskRef[0].cancel();
                    z.remove();
                    releaseChunkArea(w, targetLoc);
                    future.complete(new TestResult("AT-19", false,
                            String.format("Timed out after 600 ticks; remaining dist=%.2f (required <= 3.0)", dist)));
                }
            }, 10L, 10L);
        }, 20L);
        return future;
    }

    /**
     * BUG-1: Calls real HordeManager.spawnWaveCore repeatedly until per-player cap (200) is reached,
     * proves next wave spawns 0 new zombies, then fills a second state to global cap (400)
     * and proves a third state's wave spawns 0 new zombies.
     */
    private CompletableFuture<TestResult> testAt20AliveCap200AndGlobalCap400Async() {
        CompletableFuture<TestResult> future = new CompletableFuture<>();
        HordesConfig cfg = plugin.getHordesConfig();
        HordeManager hm = plugin.getHordeManager();
        World w = mainWorld();
        Location origin = safeTestLoc();
        holdChunkArea(w, origin);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        HordePlayerState st1 = new HordePlayerState(id1, "At20_P1", 10);
        HordePlayerState st2 = new HordePlayerState(id2, "At20_P2", 10);
        HordePlayerState st3 = new HordePlayerState(id3, "At20_P3", 10);
        st1.setActive(true);
        st2.setActive(true);
        st3.setActive(true);
        hm.registerState(st1);
        hm.registerState(st2);
        hm.registerState(st3);

        // Fill st1 to 200 via 8 real calls to spawnWaveCore (25 each, spaced by 25 ticks)
        CompletableFuture<Void> fill1 = fillStateViaWaves(hm, origin, id1, st1, 8);
        fill1.thenCompose(ignored -> {
            int p1CountAtCap = st1.getActiveEntities().size();
            int overflowP1 = hm.spawnWaveCore(origin, null, id1, st1, cfg.spawnAmount, "avoid_fluids");
            // Fill st2 to 200 via 8 real calls to spawnWaveCore (global now 400)
            return fillStateViaWaves(hm, origin, id2, st2, 8).thenApply(ignored2 -> new int[]{p1CountAtCap, overflowP1});
        }).whenComplete((metrics, err) -> {
            try {
                if (err != null) {
                    future.complete(new TestResult("AT-20", false, "Exception: " + err.getMessage()));
                    return;
                }
                int p1AtCap = metrics[0];
                int overflowP1 = metrics[1];
                int globalBeforeP3 = hm.countGlobalHordeZombies();
                int overflowGlobal = hm.spawnWaveCore(origin, null, id3, st3, cfg.spawnAmount, "avoid_fluids");
                boolean ok = p1AtCap == cfg.hordeSpawnMax
                        && overflowP1 == 0
                        && globalBeforeP3 == cfg.globalZombieCap
                        && overflowGlobal == 0;
                future.complete(new TestResult("AT-20", ok,
                        String.format("p1AtCap=%d/%d, nextWaveP1=%d, global=%d/%d, nextWaveP3=%d",
                                p1AtCap, cfg.hordeSpawnMax, overflowP1, globalBeforeP3, cfg.globalZombieCap, overflowGlobal)));
            } finally {
                cleanupStateEntities(st1);
                cleanupStateEntities(st2);
                cleanupStateEntities(st3);
                hm.removeState(id1);
                hm.removeState(id2);
                hm.removeState(id3);
                releaseChunkArea(w, origin);
            }
        });
        return future;
    }

    private CompletableFuture<Void> fillStateViaWaves(
            HordeManager hm, Location origin, UUID ownerId, HordePlayerState state, int waveCount
    ) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < waveCount; i++) {
            chain = chain.thenCompose(ignored -> {
                CompletableFuture<Void> step = new CompletableFuture<>();
                hm.spawnWaveCore(origin, null, ownerId, state, plugin.getHordesConfig().spawnAmount, "avoid_fluids");
                // 5 batches * 4 ticks = 16 ticks; wait 22 ticks so all batches finish before next wave
                Bukkit.getScheduler().runTaskLater(kit, () -> step.complete(null), 22L);
                return step;
            });
        }
        return chain;
    }

    /** BUG-8: Informational load test — spawns 200 horde zombies, waits 100 ticks active, records MSPT & TPS. */
    private CompletableFuture<TestResult> testAt21Load200ZombiesInformationalAsync() {
        CompletableFuture<TestResult> future = new CompletableFuture<>();
        HordeManager hm = plugin.getHordeManager();
        World w = mainWorld();
        Location loc = safeTestLoc();
        holdChunkArea(w, loc);
        List<Zombie> spawned = new ArrayList<>(200);
        for (int i = 0; i < 200; i++) {
            Zombie z = hm.spawnHordeZombieAt(loc, null, null);
            if (z != null) spawned.add(z);
        }
        Bukkit.getScheduler().runTaskLater(kit, () -> {
            try {
                long alive = spawned.stream().filter(z -> z.isValid() && !z.isDead()).count();
                double mspt = Bukkit.getAverageTickTime();
                double[] tps = Bukkit.getTPS();
                double tps1m = tps.length > 0 ? tps[0] : 20.0;
                future.complete(TestResult.info("AT-21",
                        String.format("INFO: spawned=%d (alive=%d after 100t), MSPT=%.2fms, TPS_1m=%.2f (local target >= 18.0)",
                                spawned.size(), alive, mspt, tps1m)));
            } finally {
                for (Zombie z : spawned) if (z.isValid()) z.remove();
                releaseChunkArea(w, loc);
            }
        }, 100L);
        return future;
    }

    /** AT-22 (PRD v3): Changing speed-multiplier + reload applies to newly spawned zombies without exception. */
    private TestResult testAt22ReloadSpeedMultiplier() {
        HordesConfig cfg = plugin.getHordesConfig();
        double original = cfg.hordeZombieSpeedMultiplier;
        Zombie zNew = null;
        try {
            plugin.getConfig().set("horde-zombie.speed-multiplier", 2.0);
            plugin.saveConfig();
            cfg.reload();
            zNew = plugin.getHordeManager().spawnHordeZombieAt(safeTestLoc(), null, null);
            AttributeInstance spd = zNew != null ? zNew.getAttribute(Attribute.MOVEMENT_SPEED) : null;
            double expected = spd != null ? spd.getBaseValue() * 2.0 : 0.0;
            double actual = spd != null ? spd.getValue() : 0.0;
            boolean ok = Math.abs(cfg.hordeZombieSpeedMultiplier - 2.0) < 0.001 && Math.abs(actual - expected) < 0.005;
            return new TestResult("AT-22", ok, String.format("reloadedMultiplier=%.2f, actualSpeed=%.4f (exp %.4f)",
                    cfg.hordeZombieSpeedMultiplier, actual, expected));
        } finally {
            if (zNew != null && zNew.isValid()) zNew.remove();
            plugin.getConfig().set("horde-zombie.speed-multiplier", original);
            plugin.saveConfig();
            cfg.reload();
        }
    }

    /**
     * BUG-1: Calls real HordeManager.startHordeCore with tableOverride="hordes:drowned"
     * and proves state.activeTable == "hordes:default" and every spawned entity is ZOMBIE.
     */
    private CompletableFuture<TestResult> testAt23LockedTableOverrideAsync() {
        CompletableFuture<TestResult> future = new CompletableFuture<>();
        HordeManager hm = plugin.getHordeManager();
        World w = mainWorld();
        Location origin = safeTestLoc();
        holdChunkArea(w, origin);

        UUID testId = UUID.randomUUID();
        HordePlayerState state = new HordePlayerState(testId, "At23Tester", 10);
        hm.registerState(state);

        boolean started = hm.startHordeCore(origin, null, testId, state, 6000, "hordes:drowned", false);

        Bukkit.getScheduler().runTaskLater(kit, () -> {
            try {
                boolean tableLocked = "hordes:default".equals(state.getActiveTable());
                List<Entity> spawned = state.getActiveEntities().stream()
                        .map(Bukkit::getEntity)
                        .filter(Objects::nonNull)
                        .toList();
                boolean allZombies = !spawned.isEmpty() && spawned.stream().allMatch(e -> e.getType() == EntityType.ZOMBIE);
                boolean ok = started && tableLocked && allZombies;
                future.complete(new TestResult("AT-23", ok,
                        "started=" + started + ", activeTable=" + state.getActiveTable()
                                + ", spawnedCount=" + spawned.size() + ", allZombie=" + allZombies));
            } finally {
                cleanupStateEntities(state);
                hm.removeState(testId);
                releaseChunkArea(w, origin);
            }
        }, 25L);
        return future;
    }
}
