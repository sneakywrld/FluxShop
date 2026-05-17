package dev.fluxshop.shop;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.ShopItem;
import org.bukkit.entity.Player;

/**
 * Checks and manages stock limits for shop items.
 */
public class StockManager {

    private final FluxShopPlugin    plugin;
    private final dev.fluxshop.storage.StockRepository repo;

    public StockManager(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.repo   = new dev.fluxshop.storage.StockRepository(plugin.getDatabase());
    }

    /**
     * Checks if a player can buy {@code quantity} of an item.
     *
     * @return {@link TransactionProcessor.Result#SUCCESS} if allowed,
     *         or the applicable failure reason
     */
    public TransactionProcessor.Result canBuy(Player player, ShopItem item, int quantity) {
        // Bypass permission
        if (player.hasPermission("fluxshop.bypass.stock")) {
            return TransactionProcessor.Result.SUCCESS;
        }

        // Global stock limit
        if (item.getGlobalStock() >= 0) {
            int sold = repo.getGlobalSold(item.getShopId(), item.getId()).join();
            if (sold + quantity > item.getGlobalStock()) {
                return TransactionProcessor.Result.NO_STOCK;
            }
        }

        // Per-player limit
        if (item.getPlayerLimit() >= 0) {
            int bought = repo.getPlayerSold(item.getShopId(), item.getId(), player.getUniqueId()).join();
            if (bought + quantity > item.getPlayerLimit()) {
                return TransactionProcessor.Result.PLAYER_LIMIT;
            }
        }

        return TransactionProcessor.Result.SUCCESS;
    }

    /**
     * Force-restocks an item by resetting its purchase counters.
     */
    public void restock(String shopId, String itemId) {
        repo.reset(shopId, itemId);
    }

    /**
     * Force-restocks every item in a shop by resetting all purchase counters for that shop.
     */
    public void restockAll(String shopId) {
        repo.resetAll(shopId);
    }
}
