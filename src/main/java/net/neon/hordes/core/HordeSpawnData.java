package net.neon.hordes.core;

import java.util.ArrayList;
import java.util.List;

public final class HordeSpawnData {

    private String tableId;
    private String spawnType;
    private int spawnAmount;
    private int durationTicks;
    private int intervalTicks;
    private double entitySpeed;
    private final List<String> rewardCommands = new ArrayList<>();

    public HordeSpawnData(
            String tableId,
            String spawnType,
            int spawnAmount,
            int durationTicks,
            int intervalTicks,
            double entitySpeed,
            List<String> rewardCommands
    ) {
        this.tableId = tableId;
        this.spawnType = spawnType;
        this.spawnAmount = spawnAmount;
        this.durationTicks = durationTicks;
        this.intervalTicks = intervalTicks;
        this.entitySpeed = entitySpeed;
        if (rewardCommands != null) {
            this.rewardCommands.addAll(rewardCommands);
        }
    }

    public String getTableId() { return tableId; }
    public void setTableId(String tableId) { this.tableId = tableId; }
    public String getSpawnType() { return spawnType; }
    public void setSpawnType(String spawnType) { this.spawnType = spawnType; }
    public int getSpawnAmount() { return spawnAmount; }
    public void setSpawnAmount(int spawnAmount) { this.spawnAmount = spawnAmount; }
    public int getDurationTicks() { return durationTicks; }
    public void setDurationTicks(int durationTicks) { this.durationTicks = durationTicks; }
    public int getIntervalTicks() { return intervalTicks; }
    public void setIntervalTicks(int intervalTicks) { this.intervalTicks = intervalTicks; }
    public double getEntitySpeed() { return entitySpeed; }
    public void setEntitySpeed(double entitySpeed) { this.entitySpeed = entitySpeed; }
    public List<String> getRewardCommands() { return rewardCommands; }
}
