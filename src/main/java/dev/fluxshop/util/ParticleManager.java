package dev.fluxshop.util;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Version-safe particle spawning manager.
 * Reads event config from particles.* and spawns appropriate Particle enum values.
 * Handles Particle enum renames between Minecraft versions.
 */
public class ParticleManager {

    private final FluxShopPlugin plugin;
    private final Map<String, Particle> particleCache = new HashMap<>();

    public ParticleManager(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Two-arg constructor for compatibility with FluxShopPlugin's init call. */
    public ParticleManager(FluxShopPlugin plugin, dev.fluxshop.config.ConfigManager ignored) {
        this.plugin = plugin;
    }

    /**
     * Spawn particles for a named event at the player's eye location.
     *
     * @param player   target player (and origin location)
     * @param eventKey config key under particles.* (e.g. "purchase-success", "shop-open")
     */
    public void spawn(Player player, String eventKey) {
        if (!plugin.getConfigManager().getBoolean("particles.enabled", true)) return;

        String path = "particles." + eventKey;
        if (!plugin.getConfigManager().getBoolean(path + ".enabled", true)) return;

        String particleName = plugin.getConfigManager().getString(path + ".type", "");
        if (particleName.isEmpty()) return;

        int    count   = plugin.getConfigManager().getInt(path + ".count", 10);
        double offsetX = plugin.getConfigManager().getDouble(path + ".offset-x", 0.5);
        double offsetY = plugin.getConfigManager().getDouble(path + ".offset-y", 0.5);
        double offsetZ = plugin.getConfigManager().getDouble(path + ".offset-z", 0.5);
        double speed   = plugin.getConfigManager().getDouble(path + ".speed", 0.1);

        Particle particle = resolveParticle(particleName);
        if (particle == null) return;

        Location loc = player.getEyeLocation();
        try {
            player.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed);
        } catch (Exception e) {
            // Some particles require extra data — skip silently
        }
    }

    /**
     * Spawn particles at an arbitrary world location, visible to all nearby players.
     */
    public void spawnAt(Location location, String eventKey) {
        if (!plugin.getConfigManager().getBoolean("particles.enabled", true)) return;
        if (location.getWorld() == null) return;

        String path = "particles." + eventKey;
        if (!plugin.getConfigManager().getBoolean(path + ".enabled", true)) return;

        String particleName = plugin.getConfigManager().getString(path + ".type", "");
        if (particleName.isEmpty()) return;

        int    count   = plugin.getConfigManager().getInt(path + ".count", 10);
        double offsetX = plugin.getConfigManager().getDouble(path + ".offset-x", 0.5);
        double offsetY = plugin.getConfigManager().getDouble(path + ".offset-y", 0.5);
        double offsetZ = plugin.getConfigManager().getDouble(path + ".offset-z", 0.5);
        double speed   = plugin.getConfigManager().getDouble(path + ".speed", 0.1);

        Particle particle = resolveParticle(particleName);
        if (particle == null) return;

        try {
            location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
        } catch (Exception e) {
            // Skip particles that require extra data
        }
    }

    /**
     * Spawn a raw particle directly by name.
     */
    public void spawnRaw(Player player, String particleName, int count, double spread) {
        Particle particle = resolveParticle(particleName);
        if (particle == null) return;
        try {
            player.spawnParticle(particle, player.getEyeLocation(), count, spread, spread, spread, 0);
        } catch (Exception ignored) {}
    }

    private Particle resolveParticle(String name) {
        return particleCache.computeIfAbsent(name.toUpperCase(), key -> {
            try {
                return Particle.valueOf(key);
            } catch (IllegalArgumentException ignored) {}

            String fallback = getFallback(key);
            if (fallback != null) {
                try {
                    return Particle.valueOf(fallback);
                } catch (IllegalArgumentException ignored) {}
            }

            plugin.getLogger().warning("[ParticleManager] Unknown particle: " + name + " — skipping");
            return null;
        });
    }

    /**
     * Maps legacy particle names to modern equivalents.
     * Covers the major renames done in 1.13 and 1.20.5+.
     */
    private String getFallback(String key) {
        return switch (key) {
            // 1.8-1.12 → 1.13+
            case "VILLAGER_HAPPY"           -> "HAPPY_VILLAGER";
            case "SMOKE_NORMAL"             -> "SMOKE";
            case "SMOKE_LARGE"              -> "LARGE_SMOKE";
            case "SPELL_WITCH"              -> "WITCH";
            case "SPELL_MOB"                -> "ENTITY_EFFECT";
            case "SPELL_MOB_AMBIENT"        -> "AMBIENT_ENTITY_EFFECT";
            case "FIREWORKS_SPARK"          -> "FIREWORK";
            case "PORTAL"                   -> "PORTAL";
            case "ENCHANTMENT_TABLE"        -> "ENCHANT";
            case "FOOTSTEP"                 -> "BLOCK_MARKER";
            case "WATER_BUBBLE"             -> "BUBBLE";
            case "WATER_SPLASH"             -> "SPLASH";
            case "WATER_WAKE"               -> "FISHING";
            case "SUSPENDED"                -> "UNDERWATER";
            case "SUSPENDED_DEPTH"          -> "UNDERWATER";
            case "TOWN_AURA"                -> "MYCELIUM";
            case "CRIT_MAGIC"               -> "ENCHANTED_HIT";
            case "EXPLOSION_NORMAL"         -> "POOF";
            case "EXPLOSION_LARGE"          -> "EXPLOSION";
            case "EXPLOSION_HUGE"           -> "EXPLOSION_EMITTER";
            case "SNOWBALL"                 -> "ITEM_SNOWBALL";
            case "SLIME"                    -> "ITEM_SLIME";
            // 1.13+ reverse fallbacks
            case "HAPPY_VILLAGER"           -> "VILLAGER_HAPPY";
            case "ENTITY_EFFECT"            -> "SPELL_MOB";
            default -> null;
        };
    }

    /**
     * Invalidate the particle cache (called on reload).
     */
    public void reload() {
        particleCache.clear();
    }
}
