package dev.fluxshop.storage;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.TradeOffer;
import dev.fluxshop.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

/**
 * Persists completed trades to fluxshop_trade_history.
 */
public class TradeRepository {

    private final FluxShopPlugin plugin;
    private final Database       db;

    public TradeRepository(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.db     = plugin.getDatabase();
    }

    /**
     * Records a completed trade asynchronously.
     */
    public CompletableFuture<Void> record(TradeOffer offer) {
        return db.async(conn -> {
            String sql = """
                INSERT INTO fluxshop_trade_history
                (player_a, player_b, items_a, items_b, currency_a, currency_b, currency, completed_at)
                VALUES (?,?,?,?,?,?,?,?)
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, offer.getPlayerA().toString());
                ps.setString(2, offer.getPlayerB().toString());
                ps.setString(3, serializeList(offer.getItemsA().toArray(new ItemStack[0])));
                ps.setString(4, serializeList(offer.getItemsB().toArray(new ItemStack[0])));
                ps.setDouble(5, offer.getCurrencyAmountA());
                ps.setDouble(6, offer.getCurrencyAmountB());
                ps.setString(7, offer.getCurrency());
                ps.setLong(8,   System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("TradeRepository.record failed: " + e.getMessage());
            }
            return null;
        }).thenApply(x -> null);
    }

    private String serializeList(ItemStack[] items) {
        StringJoiner joiner = new StringJoiner("|");
        for (ItemStack item : items) {
            String s = ItemSerializer.serialize(item);
            if (s != null) joiner.add(s);
        }
        return joiner.toString();
    }
}
