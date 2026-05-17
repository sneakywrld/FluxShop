package dev.fluxshop.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Cross-version ItemStack serialization via Base64 + BukkitObjectStream.
 * Works on all Spigot/Paper versions from 1.8 to 1.21+.
 */
public final class ItemSerializer {

    private ItemSerializer() {}

    /** Serializes an ItemStack to a Base64 string for database storage. */
    public static String serialize(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos)) {
            out.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            Logger.getLogger("FluxShop").warning("Failed to serialize ItemStack: " + e.getMessage());
            return null;
        }
    }

    /** Deserializes a Base64 string back into an ItemStack. Returns null on failure. */
    public static ItemStack deserialize(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bais)) {
            return (ItemStack) in.readObject();
        } catch (Exception e) {
            Logger.getLogger("FluxShop").warning("Failed to deserialize ItemStack: " + e.getMessage());
            return null;
        }
    }
}
