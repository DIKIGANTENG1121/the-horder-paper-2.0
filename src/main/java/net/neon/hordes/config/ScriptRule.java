package net.neon.hordes.config;

import org.bukkit.block.Biome;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Simplified F-17 data-driven rule model per PRD Section 13 Decision #3.
 * Supports conditions: biome, day, random, not.
 * Supports actions: set_spawntable, set_spawn_type.
 */
public record ScriptRule(
        String id,
        List<Condition> conditions,
        String setSpawntable,
        String setSpawnType
) {
    public sealed interface Condition permits BiomeCondition, DayCondition, RandomCondition, NotCondition {
        boolean test(Biome biome, int day, Random random);
    }

    public record BiomeCondition(Set<String> biomeNames) implements Condition {
        @Override
        public boolean test(Biome biome, int day, Random random) {
            if (biome == null) return false;
            String key = biome.getKey().getKey().toUpperCase();
            return biomeNames.contains(key);
        }
    }

    public record DayCondition(Integer minDay, Integer maxDay) implements Condition {
        @Override
        public boolean test(Biome biome, int day, Random random) {
            if (minDay != null && day < minDay) return false;
            if (maxDay != null && maxDay > 0 && day > maxDay) return false;
            return true;
        }
    }

    public record RandomCondition(double chance) implements Condition {
        @Override
        public boolean test(Biome biome, int day, Random random) {
            return random.nextDouble() < chance;
        }
    }

    public record NotCondition(Condition inner) implements Condition {
        @Override
        public boolean test(Biome biome, int day, Random random) {
            return inner == null || !inner.test(biome, day, random);
        }
    }

    public boolean matches(Biome biome, int day, Random random) {
        for (Condition c : conditions) {
            if (!c.test(biome, day, random)) {
                return false;
            }
        }
        return true;
    }
}
