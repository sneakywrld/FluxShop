package dev.fluxshop.nms;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * NMS handler for Minecraft 1.13 – 1.20.
 *
 * <p>Uses the modern Bukkit/Paper API (PersistentDataContainer, Adventure titles, etc.)
 * without direct NMS access where possible.
 */
@SuppressWarnings("deprecation")
public class NMSHandler_Modern implements NMSHandler {

    private static final org.bukkit.NamespacedKey NBT_KEY =
        new org.bukkit.NamespacedKey("fluxshop", "tag");

    @Override
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(
            net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', title),
            net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', subtitle),
            fadeIn, stay, fadeOut
        );
    }

    @Override
    public int getCustomModelData(ItemStack item) {
        if (item == null) return -1;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return -1;
        return meta.getCustomModelData();
    }

    @Override
    public ItemStack setCustomModelData(ItemStack item, int data) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setCustomModelData(data);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public String getNBTString(ItemStack item, String key) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        org.bukkit.NamespacedKey nk = new org.bukkit.NamespacedKey("fluxshop", sanitize(key));
        return pdc.has(nk, PersistentDataType.STRING) ? pdc.get(nk, PersistentDataType.STRING) : null;
    }

    @Override
    public ItemStack setNBTString(ItemStack item, String key, String value) {
        if (item == null) return null;
        ItemStack copy = item.clone();
        ItemMeta  meta = copy.getItemMeta();
        if (meta == null) return copy;
        org.bukkit.NamespacedKey nk = new org.bukkit.NamespacedKey("fluxshop", sanitize(key));
        meta.getPersistentDataContainer().set(nk, PersistentDataType.STRING, value);
        copy.setItemMeta(meta);
        return copy;
    }

    @Override
    public boolean hasNBTKey(ItemStack item, String key) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        org.bukkit.NamespacedKey nk = new org.bukkit.NamespacedKey("fluxshop", sanitize(key));
        return meta.getPersistentDataContainer().has(nk, PersistentDataType.STRING);
    }

    @Override
    public ItemStack createSkullFromBase64(String base64) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        // Use reflection to avoid compile-time dep on com.mojang.authlib
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass    = Class.forName("com.mojang.authlib.properties.Property");
            Object   profile = gameProfileClass.getConstructor(java.util.UUID.class, String.class)
                .newInstance(UUID.randomUUID(), "FluxShopSkull");
            Object property = propertyClass.getConstructor(String.class, String.class)
                .newInstance("textures", base64);
            Object properties = gameProfileClass.getMethod("getProperties").invoke(profile);
            properties.getClass().getMethod("put", Object.class, Object.class)
                .invoke(properties, "textures", property);
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception ignored) {}
        skull.setItemMeta(meta);
        return skull;
    }

    @Override
    public ItemStack createSkullFromPlayer(String playerName, UUID uuid) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(uuid));
            skull.setItemMeta(meta);
        }
        return skull;
    }

    @Override
    public String getSpawnerEntityType(ItemStack item) {
        try {
            if (item.getType() != Material.SPAWNER) return null;
            if (!(item.getItemMeta() instanceof BlockStateMeta bsm)) return null;
            if (!(bsm.getBlockState() instanceof org.bukkit.block.CreatureSpawner cs)) return null;
            EntityType et = cs.getSpawnedType();
            return et != null ? et.name() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ItemStack setSpawnerEntityType(ItemStack item, String entityTypeName) {
        try {
            if (item.getType() != Material.SPAWNER) return item;
            if (!(item.getItemMeta() instanceof BlockStateMeta bsm)) return item;
            if (!(bsm.getBlockState() instanceof org.bukkit.block.CreatureSpawner cs)) return item;
            cs.setSpawnedType(EntityType.valueOf(entityTypeName.toUpperCase()));
            bsm.setBlockState(cs);
            item.setItemMeta(bsm);
        } catch (Exception ignored) {}
        return item;
    }

    @Override
    public boolean supportsAdventureInventoryTitle() {
        return VersionUtil.isAtLeast(1, 14);
    }

    // Only lowercase alphanumeric + _ are valid in NamespacedKey
    private String sanitize(String key) {
        return key.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }
}
