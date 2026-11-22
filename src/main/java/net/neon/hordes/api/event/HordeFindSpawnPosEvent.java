package net.neon.hordes.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class HordeFindSpawnPosEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private Location spawnLocation;

    public HordeFindSpawnPosEvent(Player player, Location spawnLocation) {
        this.player = player;
        this.spawnLocation = spawnLocation;
    }

    public Player getPlayer() { return player; }
    public Location getSpawnLocation() { return spawnLocation; }
    public void setSpawnLocation(Location spawnLocation) { this.spawnLocation = spawnLocation; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
