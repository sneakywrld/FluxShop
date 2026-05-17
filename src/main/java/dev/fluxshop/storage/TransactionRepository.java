package dev.fluxshop.storage;

import dev.fluxshop.model.Transaction;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Async repository for transaction logging and history queries.
 */
public class TransactionRepository {

    private final Database db;

    public TransactionRepository(Database db) {
        this.db = db;
    }

    /**
     * Logs a transaction to the database asynchronously.
     */
    public CompletableFuture<Void> log(Transaction tx) {
        return db.async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO fluxshop_transactions (uuid,player,type,shop_id,category,item_id,item_name,quantity,price,currency,timestamp) VALUES (?,?,?,?,?,?,?,?,?,?,?)"
            )) {
                ps.setString(1,  tx.getPlayerUuid().toString());
                ps.setString(2,  tx.getPlayerName());
                ps.setString(3,  tx.getType().name());
                ps.setString(4,  tx.getShopId());
                ps.setString(5,  tx.getCategoryId());
                ps.setString(6,  tx.getItemId());
                ps.setString(7,  tx.getItemName());
                ps.setInt(8,     tx.getQuantity());
                ps.setDouble(9,  tx.getTotalPrice());
                ps.setString(10, tx.getCurrency());
                ps.setLong(11,   tx.getTimestamp().toEpochMilli());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    /**
     * Returns the most recent {@code limit} transactions for a player.
     */
    public CompletableFuture<List<Transaction>> getHistory(UUID uuid, int limit) {
        return db.async(conn -> {
            List<Transaction> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM fluxshop_transactions WHERE uuid=? ORDER BY timestamp DESC LIMIT ?"
            )) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(map(rs));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return list;
        });
    }

    /**
     * Returns total revenue (sum of BUY transactions) in a time window.
     */
    public CompletableFuture<Double> getTotalRevenue(Instant since) {
        return db.async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(price),0) FROM fluxshop_transactions WHERE type='BUY' AND timestamp>=?"
            )) {
                ps.setLong(1, since.toEpochMilli());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getDouble(1) : 0.0;
            } catch (SQLException e) {
                return 0.0;
            }
        });
    }

    private Transaction map(ResultSet rs) throws SQLException {
        return new Transaction(
            rs.getLong("id"),
            UUID.fromString(rs.getString("uuid")),
            rs.getString("player"),
            Transaction.Type.valueOf(rs.getString("type")),
            rs.getString("shop_id"),
            rs.getString("category"),
            rs.getString("item_id"),
            rs.getString("item_name"),
            rs.getInt("quantity"),
            rs.getDouble("price") / Math.max(1, rs.getInt("quantity")),
            rs.getString("currency"),
            Instant.ofEpochMilli(rs.getLong("timestamp"))
        );
    }
}
