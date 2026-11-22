package net.neon.hordes.listeners;

import net.neon.hordes.config.HordesConfig;
import net.neon.hordes.core.InfectionManager;
import net.neon.hordes.core.ZombiePlayerManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles infection transmission, item curing/immunity, entity conversion on death,
 * and zombie player spawning & damage rules (F-03, F-04, F-13, F-14).
 */
public final class InfectionListener implements Listener {

    private final HordesConfig config;
    private final InfectionManager infectionManager;
    private final ZombiePlayerManager zombiePlayerManager;

    public InfectionListener(
            HordesConfig config,
            InfectionManager infectionManager,
            ZombiePlayerManager zombiePlayerManager
    ) {
        this.config = config;
        this.infectionManager = infectionManager;
        this.zombiePlayerManager = zombiePlayerManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0) return;
        if (event.getDamager() instanceof LivingEntity attacker && event.getEntity() instanceof LivingEntity victim) {
            infectionManager.tryToInfect(attacker, victim);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onZombiePlayerDamaged(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!zombiePlayerManager.isZombiePlayer(entity)) return;

        if (config.zombiePlayersFireImmune) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.FIRE
                    || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                    || cause == EntityDamageEvent.DamageCause.LAVA
                    || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
                event.setCancelled(true);
                return;
            }
        }

        if (config.zombiePlayersOnlyHurtByPlayers) {
            if (!(event instanceof EntityDamageByEntityEvent byEntity) || !(byEntity.getDamager() instanceof Player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (infectionManager.isCureItem(item.getType())) {
            infectionManager.cureInfection(player, true);
        }
        infectionManager.applyImmunityIfApplicable(player, item.getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof LivingEntity target)) return;
        Player player = event.getPlayer();
        if (player.getLocation().distanceSquared(target.getLocation()) > 25.0) return; // 5-block range verified in JAR

        ItemStack held = player.getInventory().getItemInMainHand();
        if (infectionManager.isInfected(target) && infectionManager.isCureItem(held.getType())) {
            infectionManager.cureInfection(target, true);
            infectionManager.applyImmunityIfApplicable(target, held.getType());
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - 1);
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        boolean wasInfected = infectionManager.isInfected(player);
        infectionManager.cureInfection(player, false);
        infectionManager.resetInfectionCount(player); // Verified: cured count resets on death

        boolean shouldSpawn = config.zombieGraves || (wasInfected && config.infectionSpawnsZombiePlayers);
        if (shouldSpawn) {
            List<ItemStack> toStore = new ArrayList<>();
            if (config.zombiePlayersStoreItems && !event.getKeepInventory()) {
                toStore.addAll(event.getDrops());
                event.getDrops().clear();
            }
            zombiePlayerManager.spawnFromPlayer(player, toStore);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;

        // Drop stored items from defeated zombie player
        if (entity instanceof Zombie zombie && zombiePlayerManager.isZombiePlayer(zombie)) {
            List<ItemStack> stored = zombiePlayerManager.extractStoredItems(zombie);
            if (!stored.isEmpty()) {
                event.getDrops().addAll(stored);
            }
            return;
        }

        // Convert infected mob on death (F-03, AT-13)
        if (infectionManager.isInfected(entity) && config.conversions.containsKey(entity.getType())) {
            infectionManager.convertMob(entity);
        }
    }
}
