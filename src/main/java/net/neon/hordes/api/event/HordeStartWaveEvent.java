package net.neon.hordes.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class HordeStartWaveEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private int count;
    private boolean cancelled;

    public HordeStartWaveEvent(Player player, int count) {
        this.player = player;
        this.count = count;
    }

    public Player getPlayer() { return player; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = Math.max(0, count); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
