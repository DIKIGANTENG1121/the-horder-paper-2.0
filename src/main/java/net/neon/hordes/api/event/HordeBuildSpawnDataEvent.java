package net.neon.hordes.api.event;

import net.neon.hordes.core.HordeSpawnData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class HordeBuildSpawnDataEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final HordeSpawnData spawnData;

    public HordeBuildSpawnDataEvent(Player player, HordeSpawnData spawnData) {
        this.player = player;
        this.spawnData = spawnData;
    }

    public Player getPlayer() { return player; }
    public HordeSpawnData getSpawnData() { return spawnData; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
