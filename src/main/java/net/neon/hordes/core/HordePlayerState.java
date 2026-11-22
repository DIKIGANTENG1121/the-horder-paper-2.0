package net.neon.hordes.core;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class HordePlayerState {

    private final UUID playerId;
    private String lastKnownName;
    private long playtimeTicks;
    private int naturalHordeCount;
    private int nextHordeDay;
    private boolean active;
    private int remainingTicks;
    private int waveCooldownTicks;
    private String activeTable;
    private String activeSpawnType;
    private int currentWaveAmount;
    private final Set<UUID> activeEntities = new HashSet<>();

    public HordePlayerState(UUID playerId, String lastKnownName, int initialNextDay) {
        this.playerId = playerId;
        this.lastKnownName = lastKnownName;
        this.nextHordeDay = initialNextDay;
        this.activeTable = "hordes:default";
        this.activeSpawnType = "avoid_fluids";
    }

    public UUID getPlayerId() { return playerId; }
    public String getLastKnownName() { return lastKnownName; }
    public void setLastKnownName(String lastKnownName) { this.lastKnownName = lastKnownName; }
    public long getPlaytimeTicks() { return playtimeTicks; }
    public void setPlaytimeTicks(long playtimeTicks) { this.playtimeTicks = playtimeTicks; }
    public void addPlaytimeTicks(long delta) { this.playtimeTicks += delta; }
    public int getNaturalHordeCount() { return naturalHordeCount; }
    public void setNaturalHordeCount(int naturalHordeCount) { this.naturalHordeCount = naturalHordeCount; }
    public void incrementNaturalHordeCount() { this.naturalHordeCount++; }
    public int getNextHordeDay() { return nextHordeDay; }
    public void setNextHordeDay(int nextHordeDay) { this.nextHordeDay = nextHordeDay; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getRemainingTicks() { return remainingTicks; }
    public void setRemainingTicks(int remainingTicks) { this.remainingTicks = remainingTicks; }
    public int getWaveCooldownTicks() { return waveCooldownTicks; }
    public void setWaveCooldownTicks(int waveCooldownTicks) { this.waveCooldownTicks = waveCooldownTicks; }
    public String getActiveTable() { return activeTable; }
    public void setActiveTable(String activeTable) { this.activeTable = activeTable; }
    public String getActiveSpawnType() { return activeSpawnType; }
    public void setActiveSpawnType(String activeSpawnType) { this.activeSpawnType = activeSpawnType; }
    public int getCurrentWaveAmount() { return currentWaveAmount; }
    public void setCurrentWaveAmount(int currentWaveAmount) { this.currentWaveAmount = currentWaveAmount; }
    public Set<UUID> getActiveEntities() { return activeEntities; }
}
