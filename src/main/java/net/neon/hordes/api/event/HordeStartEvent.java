package net.neon.hordes.api.event;

import net.neon.hordes.core.HordeSpawnData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class HordeStartEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final HordeSpawnData spawnData;
    private final boolean natural;
    private boolean cancelled;

    public HordeStartEvent(Player player, HordeSpawnData spawnData, boolean natural) {
        this.player = player;
        this.spawnData = spawnData;
        this.natural = natural;
    }

    public Player getPlayer() { return player; }
    public HordeSpawnData getSpawnData() { return spawnData; }
    public boolean isNatural() { return natural; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
