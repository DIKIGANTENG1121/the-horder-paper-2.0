package net.neon.hordes.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class InfectEntityEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final LivingEntity attacker;
    private final LivingEntity victim;
    private double chance;
    private boolean cancelled;

    public InfectEntityEvent(LivingEntity attacker, LivingEntity victim, double chance) {
        this.attacker = attacker;
        this.victim = victim;
        this.chance = chance;
    }

    public LivingEntity getAttacker() { return attacker; }
    public LivingEntity getVictim() { return victim; }
    public double getChance() { return chance; }
    public void setChance(double chance) { this.chance = chance; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
