package net.neon.hordes.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class HordeEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final boolean completedNaturally;

    public HordeEndEvent(Player player, boolean completedNaturally) {
        this.player = player;
        this.completedNaturally = completedNaturally;
    }

    public Player getPlayer() { return player; }
    public boolean isCompletedNaturally() { return completedNaturally; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
