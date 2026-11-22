package net.neon.hordes.listeners;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.neon.hordes.config.HordesConfig;
import net.neon.hordes.core.HordeManager;
import net.neon.hordes.core.InfectionManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Random;

/**
 * Handles sleep blocking during active hordes (AT-12), join/quit state sync,
 * R3 target-lock, and R3 drops/XP multipliers for horde zombies.
 */
public final class HordeListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final HordesConfig config;
    private final HordeManager hordeManager;
    private final InfectionManager infectionManager;
    private final Random random = new Random();

    public HordeListener(HordesConfig config, HordeManager hordeManager, InfectionManager infectionManager) {
        this.config = config;
        this.hordeManager = hordeManager;
        this.infectionManager = infectionManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        hordeManager.getOrCreateState(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        infectionManager.hideBossBar(event.getPlayer());
        hordeManager.saveData();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (!config.enableHordeEvent || config.canSleepDuringHorde) return;
        Player player = event.getPlayer();

        if (hordeManager.isPlayerInHorde(player)) {
            event.setCancelled(true);
            player.sendMessage(MM.deserialize(config.cannotSleepMessage));
            return;
        }

        if (config.hordePreventsOtherPlayersSleeping && hordeManager.isAnyHordeActive()) {
            event.setCancelled(true);
            player.sendMessage(MM.deserialize(config.otherHordeSleepMessage));
        }
    }

    /**
     * R3 target-lock: prevent horde zombies from switching target to villagers or other mobs.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (!config.hordeZombieTargetLock) return;
        Entity entity = event.getEntity();
        if (!hordeManager.isHordeZombie(entity)) return;
        if (event.getTarget() != null && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    /**
     * R3 drops-multiplier and xp-multiplier on horde zombies to reduce lag during large waves.
     */
    @EventHandler
    public void onHordeZombieDeath(EntityDeathEvent event) {
        if (!hordeManager.isHordeZombie(event.getEntity())) return;

        if (config.hordeZombieDropsMultiplier < 1.0) {
            event.getDrops().removeIf(item -> random.nextDouble() > config.hordeZombieDropsMultiplier);
        }
        if (config.hordeZombieXpMultiplier < 1.0) {
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * config.hordeZombieXpMultiplier));
        }
    }
}
