package net.neon.hordes.listeners;

import net.neon.hordes.config.HordesConfig;
import net.neon.hordes.core.InfectionManager;
import net.neon.hordes.core.ZombiePlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Handles F-08 and F-15 misc vanilla behavior overrides using pure Paper API (no NMS).
 */
public final class MiscBehaviorListener implements Listener {

    private final HordesConfig config;
    private final InfectionManager infectionManager;
    private final ZombiePlayerManager zombiePlayerManager;

    public MiscBehaviorListener(
            HordesConfig config,
            InfectionManager infectionManager,
            ZombiePlayerManager zombiePlayerManager
    ) {
        this.config = config;
        this.infectionManager = infectionManager;
        this.zombiePlayerManager = zombiePlayerManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        // Cancel sunlight combustion (non-block/non-entity combust)
        if (event instanceof org.bukkit.event.entity.EntityCombustByBlockEvent
                || event instanceof org.bukkit.event.entity.EntityCombustByEntityEvent) {
            return;
        }

        if (zombiePlayerManager.isZombiePlayer(entity)) {
            if (!config.zombiePlayersBurn) event.setCancelled(true);
            return;
        }

        EntityType type = entity.getType();
        if (!config.zombiesBurn && (type == EntityType.ZOMBIE || type == EntityType.DROWNED || type == EntityType.ZOMBIE_VILLAGER)) {
            event.setCancelled(true);
        } else if (!config.skeletonsBurn && (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED)) {
            event.setCancelled(true);
        } else if (!config.zombieHorsesBurn && type == EntityType.ZOMBIE_HORSE) {
            event.setCancelled(true);
        } else if (!config.skeletonHorsesBurn && type == EntityType.SKELETON_HORSE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (!config.piglinsHoglinsConvert) {
            if (entity instanceof PiglinAbstract piglin) {
                piglin.setImmuneToZombification(true);
            } else if (entity instanceof Hoglin hoglin) {
                hoglin.setImmuneToZombification(true);
            }
        }
        if (config.zombiePiglinsHostile && entity instanceof PigZombie pigZombie) {
            pigZombie.setAngry(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (!config.zombieVillagersCanBeCured
                && event.getEntityType() == EntityType.ZOMBIE_VILLAGER
                && event.getTransformReason() == EntityTransformEvent.TransformReason.CURED) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (!config.zoglinsAttackUndead
                && event.getEntityType() == EntityType.ZOGLIN
                && event.getTarget() instanceof LivingEntity target
                && isUndead(target.getType())) {
            event.setCancelled(true);
        }
    }

    /**
     * Periodic AI retargeting tick (every 20 ticks) for Paper-API-only AI behaviors:
     * - Aggressive zombie horses targeting nearby players
     * - Piglins & Illagers hunting zombies
     * - Horses fleeing zombies when unmounted
     * - Infectors aggroing convertible mobs
     */
    public void tickMiscAi() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!(entity instanceof Mob mob) || mob.isDead()) continue;

                if (config.aggressiveZombieHorses && mob.getType() == EntityType.ZOMBIE_HORSE && mob.getTarget() == null) {
                    Player nearest = findNearestPlayer(mob, 16.0);
                    if (nearest != null) {
                        mob.setTarget(nearest);
                        mob.getPathfinder().moveTo(nearest, 1.2);
                    }
                } else if (config.zombiesScareHorses && mob instanceof Horse horse && horse.getPassengers().isEmpty()) {
                    LivingEntity zombie = findNearestZombie(horse, 10.0);
                    if (zombie != null) {
                        Vector away = horse.getLocation().toVector().subtract(zombie.getLocation().toVector());
                        if (away.lengthSquared() > 0.01) {
                            away.normalize().multiply(8.0);
                            horse.getPathfinder().moveTo(horse.getLocation().add(away), 1.3);
                        }
                    }
                } else if (config.piglinsHuntZombies && (mob.getType() == EntityType.PIGLIN || mob.getType() == EntityType.PIGLIN_BRUTE) && mob.getTarget() == null) {
                    LivingEntity zombie = findNearestZombie(mob, 16.0);
                    if (zombie != null) mob.setTarget(zombie);
                } else if (config.illagersHuntZombies && mob instanceof Illager && mob.getTarget() == null) {
                    LivingEntity zombie = findNearestZombie(mob, 16.0);
                    if (zombie != null) mob.setTarget(zombie);
                } else if (config.infectionEntitiesAggroConversions && infectionManager.canCauseInfection(mob) && mob.getTarget() == null) {
                    LivingEntity conv = findNearestConvertible(mob, 14.0);
                    if (conv != null) mob.setTarget(conv);
                }
            }
        }
    }

    private Player findNearestPlayer(Mob mob, double radius) {
        Player best = null;
        double bestDistSq = radius * radius;
        for (Player p : mob.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (d < bestDistSq) {
                bestDistSq = d;
                best = p;
            }
        }
        return best;
    }

    private LivingEntity findNearestZombie(LivingEntity source, double radius) {
        Collection<Entity> nearby = source.getWorld().getNearbyEntities(source.getLocation(), radius, 6.0, radius);
        for (Entity e : nearby) {
            if (e instanceof Zombie z && !z.isDead()) return z;
        }
        return null;
    }

    private LivingEntity findNearestConvertible(LivingEntity source, double radius) {
        Collection<Entity> nearby = source.getWorld().getNearbyEntities(source.getLocation(), radius, 6.0, radius);
        for (Entity e : nearby) {
            if (e instanceof LivingEntity le && !le.isDead() && !(le instanceof Player)
                    && config.conversions.containsKey(le.getType())) {
                return le;
            }
        }
        return null;
    }

    private boolean isUndead(EntityType type) {
        return switch (type) {
            case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED, SKELETON, STRAY, BOGGED, WITHER_SKELETON,
                 ZOMBIFIED_PIGLIN, ZOGLIN, ZOMBIE_HORSE, SKELETON_HORSE, PHANTOM, WITHER -> true;
            default -> false;
        };
    }
}
