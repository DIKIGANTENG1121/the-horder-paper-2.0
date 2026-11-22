package net.neon.hordes.config;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

public record SpawnTableEntry(
        EntityType entityType,
        int weight,
        int firstDay,
        int lastDay,
        Material mainHand,
        Material helmet,
        List<PassengerSpec> passengers
) {
    public record PassengerSpec(EntityType entityType, Material mainHand, Material helmet) {}

    public boolean isValidForDay(int day) {
        if (day < firstDay) {
            return false;
        }
        return lastDay <= 0 || day <= lastDay;
    }
}
