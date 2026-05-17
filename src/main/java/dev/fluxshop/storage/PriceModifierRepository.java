package dev.fluxshop.storage;

import dev.fluxshop.FluxShopPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages per-player item price modifiers.
 *
 * <p>Modifiers are stored in {@code fluxshop_price_modifiers} and cached in memory
 * for fast lookup during price calculations. A modifier of 0.9 = 10% discount;
 * 1.2 = 20% markup; 1.0 = no change (default).
 *
 * <p>Modifiers can be set with an optional expiry. {@code durationMs == 0} means
 * the modifier never expires (stored as {@code Long.MAX_VALUE}).
 */
public class PriceModifierRepository {

    private final FluxShopPlugin plugin;
    private final Database       db;

    /** Cache key: "uuid:itemId" → [multiplier, expiresAt ms] */
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();

    public PriceModifierRepository(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.db     = plugin.getDatabase();
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Sets or replaces a price modifier for a specific player and item.
     *
     * @param uuid       target player UUID
     * @param itemId     item identifier (from {@link dev.fluxshop.model.ShopItem#getId()})
     * @param multiplier the price multiplier (e.g. 0.9 for 10% discount)
     * @param durationMs how long the modifier lasts in milliseconds; 0 = permanent
     */
    public void setModifier(UUID uuid, String itemId, double multiplier, long durationMs) {
        long expiresAt = durationMs > 0 ? System.currentTimeMillis() + durationMs : Long.MAX_VALUE;
        String key = uuid + ":" + itemId;
        cache.put(key, new double[]{multiplier, expiresAt});

        db.async(conn -> {
            try {
                boolean mysql = conn.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
                String sql = mysql
                    ? "INSERT INTO fluxshop_price_modifiers (uuid, item_id, modifier, expires_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE modifier=?, expires_at=?"
                    : "INSERT OR REPLACE INTO fluxshop_price_modifiers (uuid, item_id, modifier, expires_at) VALUES (?, ?, ?, ?)";
                try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, itemId);
                    ps.setDouble(3, multiplier);
                    ps.setLong(4, expiresAt);
                    if (mysql) {
                        ps.setDouble(5, multiplier);
                        ps.setLong(6, expiresAt);
                    }
                    ps.executeUpdate();
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().warning("[PriceModifier] Failed to persist modifier: " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * Removes a modifier for a specific player and item.
     */
    public void removeModifier(UUID uuid, String itemId) {
        cache.remove(uuid + ":" + itemId);
        db.execute("DELETE FROM fluxshop_price_modifiers WHERE uuid=? AND item_id=?",
            uuid.toString(), itemId);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns the active price multiplier for the given player and item.
     * Returns {@code 1.0} if no modifier is set or if it has expired.
     *
     * <p>This is called on every price calculation — it must be synchronous and fast.
     * The in-memory cache guarantees O(1) lookup with no I/O.
     */
    public double getModifier(UUID uuid, String itemId) {
        String key = uuid + ":" + itemId;
        double[] entry = cache.get(key);
        if (entry == null) return 1.0;

        if (System.currentTimeMillis() > (long) entry[1]) {
            // Expired — evict and delete
            cache.remove(key);
            db.execute("DELETE FROM fluxshop_price_modifiers WHERE uuid=? AND item_id=?",
                uuid.toString(), itemId);
            return 1.0;
        }
        return entry[0];
    }

    /**
     * Returns true if the player has any active modifier (used for display purposes).
     */
    public boolean hasModifier(UUID uuid, String itemId) {
        return getModifier(uuid, itemId) != 1.0;
    }

    // ── Warm cache on player join ─────────────────────────────────────────────

    /**
     * Pre-loads all active modifiers for a player into the cache when they join.
     * Expired entries are skipped and cleaned up in the DB.
     */
    public CompletableFuture<Void> loadForPlayer(UUID uuid) {
        return db.async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT item_id, modifier, expires_at FROM fluxshop_price_modifiers WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                long now = System.currentTimeMillis();
                while (rs.next()) {
                    long expiresAt = rs.getLong("expires_at");
                    if (expiresAt <= now) continue; // expired — skip; periodic cleanup will remove it
                    cache.put(uuid + ":" + rs.getString("item_id"),
                        new double[]{rs.getDouble("modifier"), expiresAt});
                }
            } catch (Exception e) {
                plugin.getLogger().warning("PriceModifierRepository.loadForPlayer failed: " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * Evicts all cache entries for a player on disconnect (they'll be re-loaded on next join).
     */
    public void evictPlayer(UUID uuid) {
        String prefix = uuid + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Removes all expired entries from the DB (called periodically by the plugin).
     */
    public void purgeExpired() {
        db.execute("DELETE FROM fluxshop_price_modifiers WHERE expires_at<?",
            System.currentTimeMillis());
    }
}
