package dev.fluxshop.storage;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.BlackMarketListing;
import dev.fluxshop.util.ItemSerializer;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Async DB operations for Black Market rotation state.
 */
public class BlackMarketRepository {

    private final FluxShopPlugin plugin;
    private final Database       db;

    public BlackMarketRepository(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.db     = plugin.getDatabase();
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Inserts a new listing and sets its generated id. */
    public CompletableFuture<BlackMarketListing> insert(BlackMarketListing listing) {
        return db.async(conn -> {
            String sql = """
                INSERT INTO fluxshop_blackmarket
                (item_data, price, currency, stock, purchased, next_refresh, max_per_player)
                VALUES (?,?,?,?,?,?,?)
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, ItemSerializer.serialize(listing.getItem()));
                ps.setDouble(2, listing.getPrice());
                ps.setString(3, listing.getCurrency());
                ps.setInt(4,    listing.getStock());
                ps.setInt(5,    listing.getPurchased());
                ps.setLong(6,   listing.getNextRefresh().toEpochMilli());
                ps.setInt(7,    listing.getMaxPerPlayer());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) listing.setId(keys.getLong(1));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("BlackMarketRepository.insert failed: " + e.getMessage());
            }
            return listing;
        });
    }

    /** Increments the purchased count and records a per-player purchase. */
    public void recordPurchase(long listingId, UUID buyerUuid, int quantity) {
        db.execute("UPDATE fluxshop_blackmarket SET purchased=purchased+? WHERE id=?", quantity, listingId);
        db.async(conn -> {
            try {
                boolean mysql = conn.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
                String sql = mysql
                    ? "INSERT INTO fluxshop_bm_purchases (listing_id, uuid, quantity) VALUES (?,?,?) ON DUPLICATE KEY UPDATE quantity=quantity+?"
                    : "INSERT INTO fluxshop_bm_purchases (listing_id, uuid, quantity) VALUES (?,?,?) ON CONFLICT(listing_id, uuid) DO UPDATE SET quantity=quantity+?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, listingId);
                    ps.setString(2, buyerUuid.toString());
                    ps.setInt(3, quantity);
                    ps.setInt(4, quantity);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("BlackMarketRepository.recordPurchase failed: " + e.getMessage());
            }
            return null;
        });
    }

    /** Clears all current rotation listings. */
    public void clearAll() {
        db.execute("DELETE FROM fluxshop_blackmarket");
        db.execute("DELETE FROM fluxshop_bm_purchases");
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns all current rotation listings. */
    public CompletableFuture<List<BlackMarketListing>> getAll() {
        return db.async(conn -> {
            List<BlackMarketListing> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM fluxshop_blackmarket")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(fromRow(rs));
            } catch (SQLException e) {
                plugin.getLogger().warning("BlackMarketRepository.getAll failed: " + e.getMessage());
            }
            return list;
        });
    }

    /** Returns how many of this listing the player has already purchased. */
    public CompletableFuture<Integer> getPlayerPurchases(long listingId, UUID uuid) {
        return db.async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT quantity FROM fluxshop_bm_purchases WHERE listing_id=? AND uuid=?")) {
                ps.setLong(1, listingId);
                ps.setString(2, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt("quantity");
            } catch (SQLException e) {
                plugin.getLogger().warning("BlackMarketRepository.getPlayerPurchases failed: " + e.getMessage());
            }
            return 0;
        });
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private BlackMarketListing fromRow(ResultSet rs) throws SQLException {
        BlackMarketListing l = new BlackMarketListing();
        l.setId(rs.getLong("id"));
        l.setItem(ItemSerializer.deserialize(rs.getString("item_data")));
        l.setPrice(rs.getDouble("price"));
        l.setCurrency(rs.getString("currency"));
        l.setStock(rs.getInt("stock"));
        l.setPurchased(rs.getInt("purchased"));
        l.setNextRefresh(Instant.ofEpochMilli(rs.getLong("next_refresh")));
        l.setMaxPerPlayer(rs.getInt("max_per_player"));
        return l;
    }
}
