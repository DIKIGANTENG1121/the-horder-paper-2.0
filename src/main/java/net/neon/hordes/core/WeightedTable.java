package net.neon.hordes.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Clean-room replacement for AtlasLib WeightedOutputs.
 */
public final class WeightedTable<T> {

    public record Entry<T>(T value, int weight) {}

    private final List<Entry<T>> entries = new ArrayList<>();
    private int totalWeight = 0;

    public void add(T value, int weight) {
        if (weight <= 0 || value == null) {
            return;
        }
        entries.add(new Entry<>(value, weight));
        totalWeight += weight;
    }

    public boolean isEmpty() {
        return entries.isEmpty() || totalWeight <= 0;
    }

    public Optional<T> pick(Random random) {
        if (isEmpty()) {
            return Optional.empty();
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (Entry<T> entry : entries) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return Optional.of(entry.value());
            }
        }
        return Optional.of(entries.getLast().value());
    }
}
