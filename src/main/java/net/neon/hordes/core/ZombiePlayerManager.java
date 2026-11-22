package net.neon.hordes.core;

import net.kyori.adventure.text.Component;
import net.neon.hordes.api.event.SpawnZombiePlayerEvent;
import net.neon.hordes.compat.Paper26Compat;
import net.neon.hordes.config.HordesConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages F-04 Zombie Player spawning and stored player items.
 */
public final class ZombiePlayerManager {

    public enum Variant {
        ZOMBIE, DROWNED, HUSK
    }

    private final HordesConfig config;
    private final HordesKeys keys;

    public ZombiePlayerManager(HordesConfig config, HordesKeys keys) {
        this.config = config;
        this.keys = keys;
    }

    public Variant resolveVariant(Location loc) {
        if (config.drownedPlayers && loc.getBlock().isLiquid()) {
            return Variant.DROWNED;
        }
        if (config.huskPlayers) {
            String biomeKey = loc.getBlock().getBiome().getKey().getKey().toUpperCase(Locale.ROOT);
            if (config.huskBiomes.contains(biomeKey)) {
                return Variant.HUSK;
            }
        }
        return Variant.ZOMBIE;
    }

    public @Nullable Zombie spawnFromPlayer(Player player, List<ItemStack> dropsToStore) {
        Variant variant = resolveVariant(player.getLocation());
        return spawnZombiePlayer(player.getName(), player, player.getLocation(), variant, dropsToStore);
    }

    public @Nullable Zombie spawnZombiePlayer(
            String username,
            @Nullable Player sourcePlayer,
            Location loc,
            Variant variant,
            List<ItemStack> itemsToStore
    ) {
        if (loc.getWorld() == null) return null;

        EntityType entityType = switch (variant) {
            case DROWNED -> EntityType.DROWNED;
            case HUSK -> EntityType.HUSK;
            case ZOMBIE -> EntityType.ZOMBIE;
        };

        Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, entityType);
        zombie.setAdult();
        zombie.setPersistent(true);
        zombie.setRemoveWhenFarAway(false);
        zombie.setShouldBurnInDay(config.zombiePlayersBurn);
        zombie.customName(Component.text(username));
        zombie.setCustomNameVisible(true);

        Paper26Compat.setDespawnInPeaceful(zombie, config.zombiePlayersDespawnPeaceful);

        // Server-only visual proxy for player skin: player head item
        EntityEquipment eq = zombie.getEquipment();
        if (eq != null) {
            ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skullMeta) {
                OfflinePlayer offline = sourcePlayer != null ? sourcePlayer : Bukkit.getOfflinePlayer(username);
                skullMeta.setOwningPlayer(offline);
                head.setItemMeta(skullMeta);
            }
            eq.setHelmet(head);
            eq.setHelmetDropChance(0.0f);
        }

        PersistentDataContainer pdc = zombie.getPersistentDataContainer();
        pdc.set(keys.zombiePlayerOwner, PersistentDataType.STRING, username);
        pdc.set(keys.zombiePlayerType, PersistentDataType.STRING, variant.name());

        if (config.zombiePlayersStoreItems && itemsToStore != null && !itemsToStore.isEmpty()) {
            List<ItemStack> valid = new ArrayList<>();
            for (ItemStack item : itemsToStore) {
                if (item != null && !item.getType().isAir()) {
                    valid.add(item.clone());
                }
            }
            if (!valid.isEmpty()) {
                // Verified in ItemStack Javadoc (paper-api 26.3.build.255-stable)
                byte[] serialized = ItemStack.serializeItemsAsBytes(valid);
                pdc.set(keys.zombiePlayerItems, PersistentDataType.BYTE_ARRAY, serialized);
            }
        }

        SpawnZombiePlayerEvent event = new SpawnZombiePlayerEvent(sourcePlayer, zombie);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            zombie.remove();
            return null;
        }
        return zombie;
    }

    public boolean isZombiePlayer(org.bukkit.entity.Entity entity) {
        return entity instanceof Zombie
                && entity.getPersistentDataContainer().has(keys.zombiePlayerOwner, PersistentDataType.STRING);
    }

    public double getZombiePlayerInfectivity(org.bukkit.entity.Entity entity) {
        if (!isZombiePlayer(entity)) return -1.0;
        String type = entity.getPersistentDataContainer().get(keys.zombiePlayerType, PersistentDataType.STRING);
        if ("HUSK".equals(type)) return 0.80;
        if ("DROWNED".equals(type)) return 0.70;
        return 0.75;
    }

    public List<ItemStack> extractStoredItems(Zombie zombie) {
        PersistentDataContainer pdc = zombie.getPersistentDataContainer();
        byte[] data = pdc.get(keys.zombiePlayerItems, PersistentDataType.BYTE_ARRAY);
        if (data == null || data.length == 0) {
            return List.of();
        }
        pdc.remove(keys.zombiePlayerItems);
        // Verified in ItemStack Javadoc (paper-api 26.3.build.255-stable)
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(data);
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                result.add(item);
            }
        }
        return result;
    }
}
