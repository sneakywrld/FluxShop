package dev.fluxshop.util;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Version-safe sound playback manager.
 * Maps logical event keys to configurable sound enums, handles renames between versions.
 */
public class SoundManager {

    private final FluxShopPlugin plugin;
    private final Map<String, Sound> soundCache = new HashMap<>();

    public SoundManager(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Two-arg constructor for compatibility with FluxShopPlugin's init call. */
    public SoundManager(FluxShopPlugin plugin, dev.fluxshop.config.ConfigManager ignored) {
        this.plugin = plugin;
    }

    /**
     * Play a named sound event at the player's location.
     *
     * @param player target player
     * @param eventKey config key under sounds.* (e.g. "shop-open", "purchase-success")
     */
    public void play(Player player, String eventKey) {
        if (!plugin.getConfigManager().getBoolean("sounds.enabled", true)) return;

        String path = "sounds." + eventKey;
        if (!plugin.getConfigManager().getBoolean(path + ".enabled", true)) return;
        String soundName = plugin.getConfigManager().getString(path + ".sound", "");
        if (soundName.isEmpty()) return;

        float volume = (float) plugin.getConfigManager().getDouble(path + ".volume", 1.0);
        float pitch  = (float) plugin.getConfigManager().getDouble(path + ".pitch", 1.0);

        Sound sound = resolveSound(soundName);
        if (sound == null) return;

        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /**
     * Play a named sound event at a specific location.
     */
    public void playAt(Location location, String eventKey) {
        if (!plugin.getConfigManager().getBoolean("sounds.enabled", true)) return;

        String path = "sounds." + eventKey;
        if (!plugin.getConfigManager().getBoolean(path + ".enabled", true)) return;
        String soundName = plugin.getConfigManager().getString(path + ".sound", "");
        if (soundName.isEmpty()) return;

        float volume = (float) plugin.getConfigManager().getDouble(path + ".volume", 1.0);
        float pitch  = (float) plugin.getConfigManager().getDouble(path + ".pitch", 1.0);

        Sound sound = resolveSound(soundName);
        if (sound == null) return;

        if (location.getWorld() != null) {
            location.getWorld().playSound(location, sound, volume, pitch);
        }
    }

    /**
     * Play a raw sound by enum name (cross-version safe).
     */
    public void playRaw(Player player, String soundName, float volume, float pitch) {
        Sound sound = resolveSound(soundName);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private Sound resolveSound(String name) {
        return soundCache.computeIfAbsent(name.toUpperCase(), key -> {
            // Try direct lookup first
            try {
                return Sound.valueOf(key);
            } catch (IllegalArgumentException ignored) {}

            // Try common cross-version fallbacks
            String fallback = getFallback(key);
            if (fallback != null) {
                try {
                    return Sound.valueOf(fallback);
                } catch (IllegalArgumentException ignored) {}
            }

            plugin.getLogger().warning("[SoundManager] Unknown sound: " + name + " — skipping");
            return null;
        });
    }

    /**
     * Maps old sound names to their newer equivalents for cross-version support.
     */
    private String getFallback(String key) {
        return switch (key) {
            // 1.8 → 1.13+ renames
            case "LEVEL_UP"                 -> "ENTITY_PLAYER_LEVELUP";
            case "CLICK"                    -> "UI_BUTTON_CLICK";
            case "NOTE_PLING"               -> "BLOCK_NOTE_BLOCK_PLING";
            case "CHEST_OPEN"               -> "BLOCK_CHEST_OPEN";
            case "CHEST_CLOSE"             -> "BLOCK_CHEST_CLOSE";
            case "VILLAGER_YES"             -> "ENTITY_VILLAGER_YES";
            case "VILLAGER_NO"              -> "ENTITY_VILLAGER_NO";
            case "ITEM_PICKUP"              -> "ENTITY_ITEM_PICKUP";
            case "ANVIL_USE"                -> "BLOCK_ANVIL_USE";
            case "ENDERMAN_TELEPORT"        -> "ENTITY_ENDERMAN_TELEPORT";
            case "EXPERIENCE_ORB_PICKUP"    -> "ENTITY_EXPERIENCE_ORB_PICKUP";
            case "ORB_PICKUP"               -> "ENTITY_EXPERIENCE_ORB_PICKUP";
            // 1.13 → 1.9+ fallback
            case "ENTITY_PLAYER_LEVELUP"    -> "LEVEL_UP";
            case "UI_BUTTON_CLICK"          -> "CLICK";
            case "BLOCK_NOTE_BLOCK_PLING"   -> "NOTE_PLING";
            default -> null;
        };
    }

    /**
     * Invalidate the sound cache (called on reload).
     */
    public void reload() {
        soundCache.clear();
    }
}
