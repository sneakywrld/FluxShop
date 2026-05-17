package dev.fluxshop.storage;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.AnalyticsSnapshot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Aggregation queries for the Analytics Dashboard.
 * All methods return CompletableFuture to avoid blocking the main thread.
 */
public class AnalyticsRepository {

    private final FluxShopPlugin plugin;
    private final Database       db;

    public AnalyticsRepository(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.db     = plugin.getDatabase();
    }

    // ── Top Items ─────────────────────────────────────────────────────────────

    /**
     * Returns the top N most-bought items (by total quantity) within the given time window.
     *
     * @param windowMs  look-back window in milliseconds (0 = all time)
     * @param limit     max number of results
     * @return list of [item_id, total_quantity, total_revenue]
     */
    public CompletableFuture<List<ItemStat>> getTopBought(long windowMs, int limit) {
        return db.async(conn -> {
            long since = windowMs > 0 ? System.currentTimeMillis() - windowMs : 0;
            String sql = """
                SELECT item_id, item_name, SUM(quantity) AS qty, SUM(price) AS revenue
                FROM fluxshop_transactions
                WHERE type='BUY' AND timestamp>=?
                GROUP BY item_id, item_name
                ORDER BY qty DESC
                LIMIT ?
                """;
            List<ItemStat> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, since);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    result.add(new ItemStat(
                        rs.getString("item_id"),
                        rs.getString("item_name"),
                        rs.getLong("qty"),
                        rs.getDouble("revenue")));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("AnalyticsRepository.getTopBought failed: " + e.getMessage());
            }
            return result;
        });
    }

    /**
     * Returns the top N most-sold items (by total quantity).
     */
    public CompletableFuture<List<ItemStat>> getTopSold(long windowMs, int limit) {
        return db.async(conn -> {
            long since = windowMs > 0 ? System.currentTimeMillis() - windowMs : 0;
            String sql = """
                SELECT item_id, item_name, SUM(quantity) AS qty, SUM(price) AS revenue
                FROM fluxshop_transactions
                WHERE type='SELL' AND timestamp>=?
                GROUP BY item_id, item_name
                ORDER BY qty DESC
                LIMIT ?
                """;
            List<ItemStat> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, since);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    result.add(new ItemStat(
                        rs.getString("item_id"),
                        rs.getString("item_name"),
                        rs.getLong("qty"),
                        rs.getDouble("revenue")));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("AnalyticsRepository.getTopSold failed: " + e.getMessage());
            }
            return result;
        });
    }

    // ── Revenue ───────────────────────────────────────────────────────────────

    /**
     * Returns total shop revenue (sum of all buy transactions) grouped by currency.
     */
    public CompletableFuture<Map<String, Double>> getTotalRevenue(long windowMs) {
        return db.async(conn -> {
            long since = windowMs > 0 ? System.currentTimeMillis() - windowMs : 0;
            String sql = """
                SELECT currency, SUM(price) AS total
                FROM fluxshop_transactions
                WHERE type='BUY' AND timestamp>=?
                GROUP BY currency
                """;
            Map<String, Double> result = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, since);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) result.put(rs.getString("currency"), rs.getDouble("total"));
            } catch (SQLException e) {
                plugin.getLogger().warning("AnalyticsRepository.getTotalRevenue failed: " + e.getMessage());
            }
            return result;
        });
    }

    /**
     * Total number of transactions.
     */
    public CompletableFuture<Long> getTransactionCount(long windowMs) {
        return db.async(conn -> {
            long since = windowMs > 0 ? System.currentTimeMillis() - windowMs : 0;
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM fluxshop_transactions WHERE timestamp>=?")) {
                ps.setLong(1, since);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getLong(1);
            } catch (SQLException e) {
                plugin.getLogger().warning("AnalyticsRepository.getTransactionCount failed: " + e.getMessage());
            }
            return 0L;
        });
    }

    // ── Players ───────────────────────────────────────────────────────────────

    /**
     * Returns the top N most active buyers by transaction count.
     */
    public CompletableFuture<List<PlayerStat>> getTopBuyers(long windowMs, int limit) {
        return db.async(conn -> {
            long since = windowMs > 0 ? System.currentTimeMillis() - windowMs : 0;
            String sql = """
                SELECT player, COUNT(*) AS txns, SUM(price) AS spent
                FROM fluxshop_transactions
                WHERE type='BUY' AND timestamp>=?
                GROUP BY player
                ORDER BY spent DESC
                LIMIT ?
                """;
            List<PlayerStat> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, since);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    result.add(new PlayerStat(
                        rs.getString("player"),
                        rs.getLong("txns"),
                        rs.getDouble("spent")));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("AnalyticsRepository.getTopBuyers failed: " + e.getMessage());
            }
            return result;
        });
    }

    // ── Economy Health ────────────────────────────────────────────────────────

    /**
     * Computes a simple economy health score: ratio of buy revenue to sell payouts (0..200).
     * A score near 100 indicates balanced buy/sell flow.
     */
    public CompletableFuture<Double> getEconomyHealthScore(long windowMs) {
        long since = windowMs > 0 ? System.currentTimeMillis() - windowMs : 0;
        CompletableFuture<Double> buyFuture = db.async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(price),0) FROM fluxshop_transactions WHERE type='BUY' AND timestamp>=?")) {
                ps.setLong(1, since);
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getDouble(1) : 0.0;
            } catch (SQLException e) { return 0.0; }
        });
        CompletableFuture<Double> sellFuture = db.async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(price),0) FROM fluxshop_transactions WHERE type='SELL' AND timestamp>=?")) {
                ps.setLong(1, since);
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getDouble(1) : 0.0;
            } catch (SQLException e) { return 0.0; }
        });

        return buyFuture.thenCombine(sellFuture, (buyTotal, sellTotal) -> {
            if (buyTotal == 0 && sellTotal == 0) return 100.0;
            if (sellTotal == 0) return 200.0; // all buying, no selling
            double ratio = buyTotal / sellTotal * 100.0;
            return Math.min(200.0, Math.max(0.0, ratio));
        });
    }

    // ── Snapshot factory ──────────────────────────────────────────────────────

    /**
     * Loads all analytics data concurrently and assembles it into an
     * {@link AnalyticsSnapshot} DTO.
     *
     * @param windowMs look-back window in milliseconds (0 = all-time)
     * @param topN     number of top items / players to include
     */
    public CompletableFuture<AnalyticsSnapshot> loadSnapshot(long windowMs, int topN) {
        CompletableFuture<List<ItemStat>>     boughtFuture = getTopBought(windowMs, topN);
        CompletableFuture<List<ItemStat>>     soldFuture   = getTopSold(windowMs, topN);
        CompletableFuture<Map<String, Double>> revFuture   = getTotalRevenue(0);
        CompletableFuture<Long>               txFuture     = getTransactionCount(windowMs);
        CompletableFuture<Double>             healthFuture = getEconomyHealthScore(windowMs);
        CompletableFuture<List<PlayerStat>>   buyersFuture = getTopBuyers(windowMs, topN);

        return CompletableFuture.allOf(boughtFuture, soldFuture, revFuture, txFuture, healthFuture, buyersFuture)
            .thenApply(v -> new AnalyticsSnapshot(
                System.currentTimeMillis(),
                windowMs,
                nullSafe(boughtFuture.join(), List.of()),
                nullSafe(soldFuture.join(),   List.of()),
                nullSafe(revFuture.join(),    Map.of()),
                nullSafe(txFuture.join(),     0L),
                nullSafe(healthFuture.join(), 0.0),
                nullSafe(buyersFuture.join(), List.of())
            ));
    }

    @SuppressWarnings("unchecked")
    private static <T> T nullSafe(T value, T fallback) {
        return value != null ? value : fallback;
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    public record ItemStat(String itemId, String itemName, long quantity, double revenue) {}

    public record PlayerStat(String playerName, long transactions, double totalSpent) {}
}
