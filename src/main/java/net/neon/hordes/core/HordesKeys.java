package net.neon.hordes.core;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class HordesKeys {

    public final NamespacedKey hordeOwner;
    public final NamespacedKey hordePassenger;
    public final NamespacedKey hordeZombieTag;
    public final NamespacedKey hordeSpeedModifier;
    public final NamespacedKey infectedStage;
    public final NamespacedKey infectedRemainingTicks;
    public final NamespacedKey infectionCount;
    public final NamespacedKey immunityRemainingTicks;
    public final NamespacedKey zombiePlayerOwner;
    public final NamespacedKey zombiePlayerType;
    public final NamespacedKey zombiePlayerItems;
    public final NamespacedKey speedModifierKey;

    public HordesKeys(Plugin plugin) {
        this.hordeOwner = new NamespacedKey(plugin, "horde_owner");
        this.hordePassenger = new NamespacedKey(plugin, "horde_passenger");
        this.hordeZombieTag = new NamespacedKey(plugin, "horde_zombie");
        this.hordeSpeedModifier = new NamespacedKey(plugin, "horde_zombie_speed");
        this.infectedStage = new NamespacedKey(plugin, "infected_stage");
        this.infectedRemainingTicks = new NamespacedKey(plugin, "infected_remaining_ticks");
        this.infectionCount = new NamespacedKey(plugin, "infection_count");
        this.immunityRemainingTicks = new NamespacedKey(plugin, "immunity_remaining_ticks");
        this.zombiePlayerOwner = new NamespacedKey(plugin, "zombie_player_owner");
        this.zombiePlayerType = new NamespacedKey(plugin, "zombie_player_type");
        this.zombiePlayerItems = new NamespacedKey(plugin, "zombie_player_items");
        this.speedModifierKey = new NamespacedKey(plugin, "infected_slowness");
    }
}
