package dev.fluxshop.nms;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Abstraction over version-specific Minecraft internals.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link NMSHandler_Legacy}  — 1.8–1.12 (reflection)</li>
 *   <li>{@link NMSHandler_Modern}  — 1.13–1.20 (Bukkit API)</li>
 *   <li>{@link NMSHandler_v1_21}   — 1.21+     (Paper Component API)</li>
 * </ul>
 */
public interface NMSHandler {

    // ── Title / Subtitle ──────────────────────────────────────────────────────

    /**
     * Sends a title and subtitle to a player.
     *
     * @param player   target player
     * @param title    top title text (colour codes supported)
     * @param subtitle smaller subtitle text
     * @param fadeIn   fade-in duration in ticks
     * @param stay     stay duration in ticks
     * @param fadeOut  fade-out duration in ticks
     */
    void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut);

    // ── Item NBT / Metadata ───────────────────────────────────────────────────

    /**
     * Returns the custom model data of an item, or -1 if none.
     * (Custom model data was added in 1.14.)
     */
    int getCustomModelData(ItemStack item);

    /**
     * Sets the custom model data on an item.
     * No-op on servers older than 1.14.
     */
    ItemStack setCustomModelData(ItemStack item, int data);

    /**
     * Reads a string tag from the item's NBT data.
     * Returns {@code null} if the tag does not exist.
     */
    String getNBTString(ItemStack item, String key);

    /**
     * Writes a string tag into the item's NBT data and returns the modified copy.
     */
    ItemStack setNBTString(ItemStack item, String key, String value);

    /**
     * Returns {@code true} if the item has an NBT tag with the given key.
     */
    boolean hasNBTKey(ItemStack item, String key);

    // ── Skull Textures ────────────────────────────────────────────────────────

    /**
     * Creates a player-head ItemStack with the specified Base64 texture string.
     */
    ItemStack createSkullFromBase64(String base64);

    /**
     * Creates a player-head ItemStack textured with the given player's skin.
     */
    ItemStack createSkullFromPlayer(String playerName, UUID uuid);

    // ── Spawner ───────────────────────────────────────────────────────────────

    /**
     * Returns the entity type name stored in a spawner block-item (e.g. "ZOMBIE").
     * Returns {@code null} if not a spawner or entity type is unreadable.
     */
    String getSpawnerEntityType(ItemStack spawnerItem);

    /**
     * Sets the entity type on a spawner block-item.
     *
     * @param entityTypeName Bukkit entity type name, e.g. "ZOMBIE"
     */
    ItemStack setSpawnerEntityType(ItemStack spawnerItem, String entityTypeName);

    // ── Inventory ─────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the server supports the modern Adventure text API
     * for inventory titles (1.14+).
     */
    boolean supportsAdventureInventoryTitle();
}
