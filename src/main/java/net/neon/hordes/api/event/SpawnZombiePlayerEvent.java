package net.neon.hordes.api.event;

import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpawnZombiePlayerEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final @Nullable Player sourcePlayer;
    private final Zombie zombiePlayer;
    private boolean cancelled;

    public SpawnZombiePlayerEvent(@Nullable Player sourcePlayer, Zombie zombiePlayer) {
        this.sourcePlayer = sourcePlayer;
        this.zombiePlayer = zombiePlayer;
    }

    public @Nullable Player getSourcePlayer() { return sourcePlayer; }
    public Zombie getZombiePlayer() { return zombiePlayer; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
