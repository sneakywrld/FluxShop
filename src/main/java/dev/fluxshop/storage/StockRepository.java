package dev.fluxshop.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages global and per-player stock tracking.
 */
public class StockRepository {

    private final Database db;

    public StockRepository(Database db) {
        this.db = db;
    }

    /** Sentinel used in the uuid column to represent the global (cross-player) stock row. */
    private static final String GLOBAL_SENTINEL = "__global__";

    /** Returns total quantity sold globally for an item (all players). */
    public CompletableFuture<Integer> getGlobalSold(String shopId, String itemId) {
        return db.async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(quantity_sold),0) FROM fluxshop_stock WHERE shop_id=? AND item_id=? AND uuid=?"
            )) {
                ps.setString(1, shopId);
                ps.setString(2, itemId);
                ps.setString(3, GLOBAL_SENTINEL);
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) { return 0; }
        });
    }

    /** Returns quantity purchased by a specific player. */
    public CompletableFuture<Integer> getPlayerSold(String shopId, String itemId, UUID uuid) {
        return db.async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(quantity_sold),0) FROM fluxshop_stock WHERE shop_id=? AND item_id=? AND uuid=?"
            )) {
                ps.setString(1, shopId);
                ps.setString(2, itemId);
                ps.setString(3, uuid.toString());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) { return 0; }
        });
    }

    /** Increments the global and per-player purchase counter by {@code amount}. */
    public CompletableFuture<Void> recordPurchase(String shopId, String itemId, UUID uuid, int amount) {
        return db.async(conn -> {
            // Global record — use non-NULL sentinel so UNIQUE(shop_id, item_id, uuid) fires correctly.
            // NULL values are considered distinct from each other in SQL, causing unbounded row growth.
            upsertStock(conn, shopId, itemId, GLOBAL_SENTINEL, amount);
            // Per-player record
            upsertStock(conn, shopId, itemId, uuid.toString(), amount);
            return null;
        });
    }

    /** Resets all purchase records for an item (admin restock). */
    public CompletableFuture<Void> reset(String shopId, String itemId) {
        return db.async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM fluxshop_stock WHERE shop_id=? AND item_id=?"
            )) {
                ps.setString(1, shopId);
                ps.setString(2, itemId);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
            return null;
        });
    }

    /** Resets all purchase records for every item in a shop (whole-shop admin restock). */
    public CompletableFuture<Void> resetAll(String shopId) {
        return db.async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM fluxshop_stock WHERE shop_id=?"
            )) {
                ps.setString(1, shopId);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
            return null;
        });
    }

    private void upsertStock(java.sql.Connection conn, String shopId, String itemId, String uuid, int amount) {
        try {
            // Try INSERT first, UPDATE on conflict
            String upsert = conn.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")
                ? "INSERT INTO fluxshop_stock (shop_id,item_id,uuid,quantity_sold) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE quantity_sold=quantity_sold+?"
                : "INSERT INTO fluxshop_stock (shop_id,item_id,uuid,quantity_sold) VALUES (?,?,?,?) ON CONFLICT(shop_id,item_id,uuid) DO UPDATE SET quantity_sold=quantity_sold+?";
            try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                ps.setString(1, shopId);
                ps.setString(2, itemId);
                ps.setString(3, uuid);
                ps.setInt(4, amount);
                ps.setInt(5, amount);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
    }
}
