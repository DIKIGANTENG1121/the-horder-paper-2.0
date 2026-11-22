package net.neon.hordes.core;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.neon.hordes.api.event.InfectEntityEvent;
import net.neon.hordes.api.event.InfectionDeathEvent;
import net.neon.hordes.config.HordesConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Manages F-03, F-06, F-10 (server-side visual feedback), F-13 (4 stages + slowness/hunger/reduction),
 * and F-14 (infectivity * (1 - resistance) with armor/entity protection).
 */
public final class InfectionManager {

    public static final int MAX_STAGE_INDEX = 3; // 4 stages: 0, 1, 2, 3 (verified in InfectionEventHandler.effectExpired)

    private final JavaPlugin plugin;
    private final HordesConfig config;
    private final HordesKeys keys;
    private final ZombiePlayerManager zombiePlayerManager;
    private final Random random = new Random();

    private final Set<UUID> trackedEntities = new HashSet<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();

    public InfectionManager(
            JavaPlugin plugin,
            HordesConfig config,
            HordesKeys keys,
            ZombiePlayerManager zombiePlayerManager
    ) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
        this.zombiePlayerManager = zombiePlayerManager;
    }

    public void scanLoadedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (isInfected(entity) || hasImmunity(entity)) {
                    trackedEntities.add(entity.getUniqueId());
                }
            }
        }
    }

    public boolean canCauseInfection(Entity attacker) {
        if (!config.enableMobInfection || !(attacker instanceof LivingEntity)) return false;
        if (zombiePlayerManager.isZombiePlayer(attacker)) return true;
        return config.infectors.containsKey(attacker.getType());
    }

    public boolean canBeInfected(Entity victim) {
        if (!config.enableMobInfection || !(victim instanceof LivingEntity living)) return false;
        if (living instanceof Player) {
            return config.infectPlayers;
        }
        return config.conversions.containsKey(living.getType());
    }

    /**
     * Exact formula verified from InfectionData.getInfectionChance(attacker, victim):
     * chance = infectivity * (1.0 - infection_resistance)
     */
    public double calculateInfectionChance(LivingEntity attacker, LivingEntity victim) {
        double infectivity = zombiePlayerManager.getZombiePlayerInfectivity(attacker);
        if (infectivity < 0.0) {
            infectivity = config.infectors.getOrDefault(attacker.getType(), 0.0);
        }
        double resistance = calculateInfectionResistance(victim);
        return Math.max(0.0, Math.min(1.0, infectivity * (1.0 - resistance)));
    }

    public double calculateInfectionResistance(LivingEntity victim) {
        double base = (victim instanceof Player)
                ? config.playerInfectionResistance
                : config.conversions.containsKey(victim.getType())
                    ? config.conversions.get(victim.getType()).protection()
                    : 0.0;

        double addValue = 0.0;
        double multipliedTotal = 1.0;

        EntityEquipment eq = victim.getEquipment();
        if (eq != null) {
            for (ItemStack armor : eq.getArmorContents()) {
                if (armor == null || armor.getType().isAir()) continue;
                HordesConfig.WearableProtection wp = config.wearablesProtection.get(armor.getType());
                if (wp == null) continue;
                if ("add_value".equalsIgnoreCase(wp.operation())) {
                    addValue += wp.protection();
                } else {
                    multipliedTotal *= (1.0 + wp.protection());
                }
            }
        }

        double result = (base + addValue) * multipliedTotal;
        return Math.max(0.0, Math.min(1.0, result));
    }

    public boolean tryToInfect(LivingEntity attacker, LivingEntity victim) {
        if (!canCauseInfection(attacker) || !canBeInfected(victim)) return false;
        if (isInfected(victim) || hasImmunity(victim)) return false;

        double chance = calculateInfectionChance(attacker, victim);
        InfectEntityEvent event = new InfectEntityEvent(attacker, victim, chance);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        if (random.nextDouble() < event.getChance()) {
            applyInfection(victim, 0);
            return true;
        }
        return false;
    }

    /**
     * Exact formula verified from InfectedEffect.getInfectionTime(LivingEntity):
     * duration = (int) (ticksForEffectStage * Math.pow(effectStageTickReduction, infectionCount))
     */
    public int getStageDurationTicks(LivingEntity entity) {
        int curedCount = getInfectionCount(entity);
        double scaled = config.ticksForEffectStage * Math.pow(config.effectStageTickReduction, curedCount);
        return Math.max(20, (int) Math.round(scaled));
    }

    public void applyInfection(LivingEntity entity, int stage) {
        if (hasImmunity(entity)) return;
        int duration = getStageDurationTicks(entity);
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(keys.infectedStage, PersistentDataType.INTEGER, stage);
        pdc.set(keys.infectedRemainingTicks, PersistentDataType.INTEGER, duration);
        trackedEntities.add(entity.getUniqueId());

        applyStageAttributeModifiers(entity, stage);
        if (entity instanceof Player player) {
            updatePlayerFeedback(player, stage, duration);
            if (stage == 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_INFECT, 1.0f, 0.9f);
            }
        }
    }

    public boolean isInfected(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(keys.infectedStage, PersistentDataType.INTEGER);
    }

    public int getInfectedStage(LivingEntity entity) {
        Integer stage = entity.getPersistentDataContainer().get(keys.infectedStage, PersistentDataType.INTEGER);
        return stage == null ? -1 : stage;
    }

    public int getInfectedRemainingTicks(LivingEntity entity) {
        Integer ticks = entity.getPersistentDataContainer().get(keys.infectedRemainingTicks, PersistentDataType.INTEGER);
        return ticks == null ? 0 : ticks;
    }

    public int getInfectionCount(LivingEntity entity) {
        Integer count = entity.getPersistentDataContainer().get(keys.infectionCount, PersistentDataType.INTEGER);
        return count == null ? 0 : count;
    }

    public void resetInfectionCount(LivingEntity entity) {
        entity.getPersistentDataContainer().remove(keys.infectionCount);
    }

    public boolean hasImmunity(LivingEntity entity) {
        Integer ticks = entity.getPersistentDataContainer().get(keys.immunityRemainingTicks, PersistentDataType.INTEGER);
        return ticks != null && ticks > 0;
    }

    public int getImmunityRemainingTicks(LivingEntity entity) {
        Integer ticks = entity.getPersistentDataContainer().get(keys.immunityRemainingTicks, PersistentDataType.INTEGER);
        return ticks == null ? 0 : ticks;
    }

    public boolean isCureItem(Material material) {
        return config.cures.contains(material);
    }

    /**
     * Verified in InfectionData.applyImmunity: durationSeconds * 20 ticks, and clears active infection.
     */
    public void applyImmunityIfApplicable(LivingEntity entity, Material material) {
        Integer seconds = config.immunityItemsSeconds.get(material);
        if (seconds == null || seconds <= 0) return;
        if (isInfected(entity)) {
            cureInfection(entity, false);
        }
        entity.getPersistentDataContainer().set(keys.immunityRemainingTicks, PersistentDataType.INTEGER, seconds * 20);
        trackedEntities.add(entity.getUniqueId());
    }

    public boolean cureInfection(LivingEntity entity, boolean incrementCuredCount) {
        if (!isInfected(entity)) return false;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.remove(keys.infectedStage);
        pdc.remove(keys.infectedRemainingTicks);
        if (incrementCuredCount) {
            int next = getInfectionCount(entity) + 1;
            pdc.set(keys.infectionCount, PersistentDataType.INTEGER, next);
        }
        removeSlownessModifier(entity);
        if (entity instanceof Player player) {
            hideBossBar(player);
            player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.8f, 1.2f);
        }
        return true;
    }

    public void tick(int intervalTicks) {
        Iterator<UUID> it = trackedEntities.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity raw = Bukkit.getEntity(id);
            if (!(raw instanceof LivingEntity entity) || entity.isDead() || !entity.isValid()) {
                if (playerBossBars.containsKey(id)) {
                    playerBossBars.remove(id);
                }
                it.remove();
                continue;
            }

            boolean stillTracked = false;
            PersistentDataContainer pdc = entity.getPersistentDataContainer();

            // Tick immunity
            Integer imm = pdc.get(keys.immunityRemainingTicks, PersistentDataType.INTEGER);
            if (imm != null) {
                int nextImm = imm - intervalTicks;
                if (nextImm <= 0) {
                    pdc.remove(keys.immunityRemainingTicks);
                } else {
                    pdc.set(keys.immunityRemainingTicks, PersistentDataType.INTEGER, nextImm);
                    stillTracked = true;
                }
            }

            // Tick infection
            Integer stage = pdc.get(keys.infectedStage, PersistentDataType.INTEGER);
            Integer rem = pdc.get(keys.infectedRemainingTicks, PersistentDataType.INTEGER);
            if (stage != null && rem != null) {
                int nextRem = rem - intervalTicks;

                // Hunger exhaustion on later stages (stage >= 1), verified 0.03f in InfectedEffect.applyEffectTick
                if (config.infectHunger && stage >= 1 && entity instanceof Player player) {
                    float extraExhaustion = 0.03f * stage * (intervalTicks / 20.0f);
                    player.setExhaustion(player.getExhaustion() + extraExhaustion);
                }

                // Server-side particles (F-10)
                if (config.spawnParticles) {
                    entity.getWorld().spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            entity.getLocation().add(0, 1.0, 0),
                            2 + stage,
                            0.3, 0.5, 0.3
                    );
                }

                if (nextRem <= 0) {
                    if (stage < MAX_STAGE_INDEX) {
                        applyInfection(entity, stage + 1);
                        stillTracked = true;
                    } else {
                        handleFinalStageExpiry(entity);
                    }
                } else {
                    pdc.set(keys.infectedRemainingTicks, PersistentDataType.INTEGER, nextRem);
                    if (entity instanceof Player player) {
                        updatePlayerFeedback(player, stage, nextRem);
                    }
                    stillTracked = true;
                }
            }

            if (!stillTracked) {
                it.remove();
            }
        }
    }

    private void handleFinalStageExpiry(LivingEntity entity) {
        InfectionDeathEvent deathEvent = new InfectionDeathEvent(entity);
        Bukkit.getPluginManager().callEvent(deathEvent);
        if (deathEvent.isCancelled()) {
            cureInfection(entity, false);
            return;
        }

        if (entity instanceof Player player) {
            // Lethal infection damage triggers PlayerDeathEvent where ZombiePlayer is spawned
            player.damage(Float.MAX_VALUE);
        } else {
            convertMob(entity);
        }
    }

    public @Nullable LivingEntity convertMob(LivingEntity victim) {
        HordesConfig.ConversionSpec spec = config.conversions.get(victim.getType());
        if (spec == null) return null;
        Location loc = victim.getLocation();
        if (loc.getWorld() == null) return null;

        cureInfection(victim, false);
        victim.remove();

        Entity spawned = loc.getWorld().spawnEntity(loc, spec.convertsTo());
        if (spawned instanceof LivingEntity converted) {
            if (spec.maxHealth() != null) {
                AttributeInstance hp = converted.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) {
                    hp.setBaseValue(spec.maxHealth());
                    converted.setHealth(spec.maxHealth());
                }
            }
            if (spec.customName() != null && !spec.customName().isBlank()) {
                converted.customName(Component.text(spec.customName()));
            }
            return converted;
        }
        return null;
    }

    private void applyStageAttributeModifiers(LivingEntity entity, int stage) {
        removeSlownessModifier(entity);
        if (!config.infectSlowness || stage < 1) return;
        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        // Verified in InfectedEffect.addAttributeModifiers: -0.1 ADD_MULTIPLIED_TOTAL
        AttributeModifier mod = new AttributeModifier(
                keys.speedModifierKey,
                -0.1 * stage,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1
        );
        speed.addModifier(mod);
    }

    public void removeSlownessModifier(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(keys.speedModifierKey);
        }
    }

    private void updatePlayerFeedback(Player player, int stage, int remainingTicks) {
        int displayStage = stage + 1;
        int seconds = Math.max(0, remainingTicks / 20);
        String label = "Infected (Stage " + displayStage + "/4) — " + seconds + "s";

        if (config.showBossbar) {
            float progress = Math.max(0.0f, Math.min(1.0f,
                    (float) remainingTicks / Math.max(1, getStageDurationTicks(player))));
            BossBar bar = playerBossBars.computeIfAbsent(player.getUniqueId(), uuid -> {
                BossBar created = BossBar.bossBar(
                        Component.text(label, NamedTextColor.YELLOW),
                        progress,
                        BossBar.Color.GREEN,
                        BossBar.Overlay.NOTCHED_6
                );
                player.showBossBar(created);
                return created;
            });
            bar.name(Component.text(label, stage >= 2 ? NamedTextColor.RED : NamedTextColor.YELLOW));
            bar.progress(progress);
            bar.color(stage >= 2 ? BossBar.Color.RED : BossBar.Color.YELLOW);
        }

        if (config.showActionbar) {
            player.sendActionBar(Component.text(label, stage >= 2 ? NamedTextColor.RED : NamedTextColor.GOLD));
        }
    }

    public void hideBossBar(Player player) {
        BossBar bar = playerBossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideBossBar(player);
        }
        playerBossBars.clear();
    }
}
