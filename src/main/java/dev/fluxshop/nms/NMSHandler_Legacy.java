package dev.fluxshop.nms;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.UUID;

/**
 * NMS handler for Minecraft 1.8 – 1.12.2.
 *
 * <p>All version-specific calls are made via reflection to avoid compile-time
 * dependencies on NMS classes that change between versions.
 */
@SuppressWarnings({"deprecation", "unchecked"})
public class NMSHandler_Legacy implements NMSHandler {

    /** NMS package prefix, e.g. "net.minecraft.server.v1_8_R3" */
    private final String nmsPrefix;
    /** OBC package prefix, e.g. "org.bukkit.craftbukkit.v1_8_R3" */
    private final String obcPrefix;

    public NMSHandler_Legacy() {
        String serverPackage = Bukkit.getServer().getClass().getPackage().getName();
        String version = serverPackage.split("\\.")[3]; // e.g. v1_8_R3
        this.nmsPrefix = "net.minecraft.server." + version + ".";
        this.obcPrefix = "org.bukkit.craftbukkit." + version + ".";
    }

    // ── Title ─────────────────────────────────────────────────────────────────

    @Override
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Class<?> chatSerializer  = Class.forName(nmsPrefix + "IChatBaseComponent$ChatSerializer");
            Class<?> chatComponent   = Class.forName(nmsPrefix + "IChatBaseComponent");
            Class<?> packetTitle     = Class.forName(nmsPrefix + "PacketPlayOutTitle");
            Class<?> enumTitle       = Class.forName(nmsPrefix + "PacketPlayOutTitle$EnumTitleAction");

            Method   aMethod    = chatSerializer.getDeclaredMethod("a", String.class);
            Object   titleComp  = aMethod.invoke(null, "{\"text\":\"" + escapeJson(title) + "\"}");
            Object   subComp    = aMethod.invoke(null, "{\"text\":\"" + escapeJson(subtitle) + "\"}");

            Object   timesPacket = packetTitle
                .getConstructor(enumTitle, chatComponent, int.class, int.class, int.class)
                .newInstance(Enum.valueOf((Class<Enum>) enumTitle, "TIMES"), null, fadeIn, stay, fadeOut);
            Object   titlePacket = packetTitle
                .getConstructor(enumTitle, chatComponent, int.class, int.class, int.class)
                .newInstance(Enum.valueOf((Class<Enum>) enumTitle, "TITLE"), titleComp, fadeIn, stay, fadeOut);
            Object   subPacket   = packetTitle
                .getConstructor(enumTitle, chatComponent, int.class, int.class, int.class)
                .newInstance(Enum.valueOf((Class<Enum>) enumTitle, "SUBTITLE"), subComp, fadeIn, stay, fadeOut);

            sendPacket(player, timesPacket);
            sendPacket(player, titlePacket);
            sendPacket(player, subPacket);
        } catch (Exception e) {
            // Fallback — action bar or chat
            player.sendMessage(title);
        }
    }

    // ── Custom Model Data (1.14+ only — no-op here) ───────────────────────────

    @Override
    public int getCustomModelData(ItemStack item) {
        return -1; // not available in 1.8–1.12
    }

    @Override
    public ItemStack setCustomModelData(ItemStack item, int data) {
        return item; // no-op
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public String getNBTString(ItemStack item, String key) {
        try {
            Object nmsItem = asNMSCopy(item);
            Object tag     = getOrCreateTag(nmsItem);
            Method hasKey  = tag.getClass().getMethod("hasKey", String.class);
            if (!(boolean) hasKey.invoke(tag, key)) return null;
            Method getString = tag.getClass().getMethod("getString", String.class);
            return (String) getString.invoke(tag, key);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ItemStack setNBTString(ItemStack item, String key, String value) {
        try {
            Object nmsItem = asNMSCopy(item);
            Object tag     = getOrCreateTag(nmsItem);
            tag.getClass().getMethod("setString", String.class, String.class).invoke(tag, key, value);
            // Set tag back — must use tag.getClass() (NBTTagCompound), NOT getSuperclass() (NBTBase);
            // NMS declares setTag(NBTTagCompound), so using the superclass causes NoSuchMethodException.
            nmsItem.getClass().getMethod("setTag", tag.getClass()).invoke(nmsItem, tag);
            return asBukkitCopy(nmsItem);
        } catch (Exception e) {
            return item;
        }
    }

    @Override
    public boolean hasNBTKey(ItemStack item, String key) {
        return getNBTString(item, key) != null;
    }

    // ── Skull ─────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("deprecation")
    public ItemStack createSkullFromBase64(String base64) {
        // Use runtime material lookup — SKULL_ITEM only exists pre-1.13
        Material skullMat = Material.matchMaterial("SKULL_ITEM");
        if (skullMat == null) skullMat = Material.matchMaterial("PLAYER_HEAD");
        if (skullMat == null) skullMat = Material.PLAYER_HEAD;
        ItemStack skull = new ItemStack(skullMat, 1, (short) 3);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        // Use reflection to avoid compile-time dep on com.mojang.authlib
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass    = Class.forName("com.mojang.authlib.properties.Property");
            Object   profile = gameProfileClass.getConstructor(UUID.class, String.class)
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
    @SuppressWarnings("deprecation")
    public ItemStack createSkullFromPlayer(String playerName, UUID uuid) {
        Material skullMat = Material.matchMaterial("SKULL_ITEM");
        if (skullMat == null) skullMat = Material.PLAYER_HEAD;
        ItemStack skull = new ItemStack(skullMat, 1, (short) 3);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwner(playerName);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    // ── Spawner ───────────────────────────────────────────────────────────────

    private static Material SPAWNER_MATERIAL = null;
    private static Material getSpawnerMaterial() {
        if (SPAWNER_MATERIAL == null) {
            SPAWNER_MATERIAL = Material.matchMaterial("MOB_SPAWNER");
            if (SPAWNER_MATERIAL == null) SPAWNER_MATERIAL = Material.matchMaterial("SPAWNER");
            if (SPAWNER_MATERIAL == null) SPAWNER_MATERIAL = Material.SPAWNER;
        }
        return SPAWNER_MATERIAL;
    }

    @Override
    public String getSpawnerEntityType(ItemStack item) {
        try {
            if (item.getType() != getSpawnerMaterial()) return null;
            if (!(item.getItemMeta() instanceof BlockStateMeta bsm)) return null;
            org.bukkit.block.CreatureSpawner cs = (org.bukkit.block.CreatureSpawner) bsm.getBlockState();
            EntityType et = cs.getSpawnedType();
            return et != null ? et.name() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ItemStack setSpawnerEntityType(ItemStack item, String entityTypeName) {
        try {
            if (item.getType() != getSpawnerMaterial()) return item;
            if (!(item.getItemMeta() instanceof BlockStateMeta bsm)) return item;
            org.bukkit.block.CreatureSpawner cs = (org.bukkit.block.CreatureSpawner) bsm.getBlockState();
            cs.setSpawnedType(EntityType.valueOf(entityTypeName.toUpperCase()));
            bsm.setBlockState(cs);
            item.setItemMeta(bsm);
        } catch (Exception ignored) {}
        return item;
    }

    @Override
    public boolean supportsAdventureInventoryTitle() {
        return false;
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private Object asNMSCopy(ItemStack item) throws Exception {
        Class<?> craftItemStack = Class.forName(obcPrefix + "inventory.CraftItemStack");
        return craftItemStack.getMethod("asNMSCopy", ItemStack.class).invoke(null, item);
    }

    private ItemStack asBukkitCopy(Object nmsItem) throws Exception {
        Class<?> craftItemStack = Class.forName(obcPrefix + "inventory.CraftItemStack");
        return (ItemStack) craftItemStack.getMethod("asBukkitCopy", nmsItem.getClass()).invoke(null, nmsItem);
    }

    private Object getOrCreateTag(Object nmsItem) throws Exception {
        Class<?> nmsItemClass = nmsItem.getClass();
        Method   getTag = nmsItemClass.getMethod("getTag");
        Object   tag    = getTag.invoke(nmsItem);
        if (tag == null) {
            Class<?> nbtTagCompound = Class.forName(nmsPrefix + "NBTTagCompound");
            tag = nbtTagCompound.getDeclaredConstructor().newInstance();
            nmsItem.getClass().getMethod("setTag", nbtTagCompound).invoke(nmsItem, tag);
        }
        return tag;
    }

    private void sendPacket(Player player, Object packet) throws Exception {
        Class<?> craftPlayer   = Class.forName(obcPrefix + "entity.CraftPlayer");
        Object   entityPlayer  = craftPlayer.getMethod("getHandle").invoke(craftPlayer.cast(player));
        Object   playerConn    = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
        playerConn.getClass().getMethod("sendPacket",
            Class.forName(nmsPrefix + "Packet")).invoke(playerConn, packet);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
