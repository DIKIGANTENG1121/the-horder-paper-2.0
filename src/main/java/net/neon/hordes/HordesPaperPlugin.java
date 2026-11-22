package net.neon.hordes;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.neon.hordes.command.HordesCommandRegistrar;
import net.neon.hordes.config.HordesConfig;
import net.neon.hordes.core.HordeManager;
import net.neon.hordes.core.HordesKeys;
import net.neon.hordes.core.InfectionManager;
import net.neon.hordes.core.ZombiePlayerManager;
import net.neon.hordes.listeners.HordeListener;
import net.neon.hordes.listeners.InfectionListener;
import net.neon.hordes.listeners.MiscBehaviorListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class HordesPaperPlugin extends JavaPlugin {

    private HordesConfig hordesConfig;
    private HordesKeys keys;
    private ZombiePlayerManager zombiePlayerManager;
    private InfectionManager infectionManager;
    private HordeManager hordeManager;
    private MiscBehaviorListener miscBehaviorListener;

    @Override
    public void onEnable() {
        this.keys = new HordesKeys(this);
        this.hordesConfig = new HordesConfig(this);
        this.hordesConfig.reload();

        this.zombiePlayerManager = new ZombiePlayerManager(hordesConfig, keys);
        this.infectionManager = new InfectionManager(this, hordesConfig, keys, zombiePlayerManager);
        this.hordeManager = new HordeManager(this, hordesConfig, keys);
        this.hordeManager.loadData();
        this.hordeManager.checkSimulationDistanceWarning();
        this.infectionManager.scanLoadedEntities();

        this.miscBehaviorListener = new MiscBehaviorListener(hordesConfig, infectionManager, zombiePlayerManager);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new HordeListener(hordesConfig, hordeManager, infectionManager), this);
        pm.registerEvents(new InfectionListener(hordesConfig, infectionManager, zombiePlayerManager), this);
        pm.registerEvents(miscBehaviorListener, this);

        // Paper 26.3 Brigadier command registration via LifecycleEvents.COMMANDS
        HordesCommandRegistrar registrar = new HordesCommandRegistrar(hordesConfig, hordeManager, zombiePlayerManager);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> registrar.register(event.registrar()));

        // Main-thread schedulers
        Bukkit.getScheduler().runTaskTimer(this, () -> hordeManager.tick(20), 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> hordeManager.tickPathingAndDoors(), 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, () -> infectionManager.tick(20), 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> miscBehaviorListener.tickMiscAi(), 40L, 40L);
        // Periodic persistence autosave every 5 minutes
        Bukkit.getScheduler().runTaskTimer(this, () -> hordeManager.saveData(), 6000L, 6000L);

        getLogger().info("HordesPaper enabled (Paper 26.3, server-only port of The Hordes 1.21.1-1.6.3f).");
    }

    @Override
    public void onDisable() {
        if (hordeManager != null) {
            hordeManager.saveData();
        }
        if (infectionManager != null) {
            infectionManager.shutdown();
        }
        getLogger().info("HordesPaper disabled cleanly.");
    }

    public HordesConfig getHordesConfig() { return hordesConfig; }
    public HordesKeys getKeys() { return keys; }
    public HordeManager getHordeManager() { return hordeManager; }
    public InfectionManager getInfectionManager() { return infectionManager; }
    public ZombiePlayerManager getZombiePlayerManager() { return zombiePlayerManager; }
}
