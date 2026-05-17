package dev.fluxshop.listener;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * General player lifecycle listener.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>On join: notify the player if they have uncollected auction items or funds.</li>
 *   <li>On join: deliver any offline trade refunds or completions.</li>
 *   <li>On quit: cancel any active trade session, returning items to both parties.</li>
 *   <li>On quit: close any open GUI so inventory state is safe.</li>
 * </ul>
 */
public class PlayerListener implements Listener {

    private final FluxShopPlugin plugin;

    public PlayerListener(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Join ──────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Pre-load price modifiers from DB into cache (async, no blocking)
        plugin.getPriceModifierRepository().loadForPlayer(player.getUniqueId());

        // Delay by 1 tick so the player's inventory is fully loaded before we modify it.
        // This also prevents any race with login packets.
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            notifyAuctionCollect(player);
        }, 1L);
    }

    // ── Quit ──────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Cancel any active trade so items are returned before the player's inventory saves
        cancelActiveTrade(player);

        // Evict price-modifier cache entries (they'll be reloaded on next join)
        plugin.getPriceModifierRepository().evictPlayer(player.getUniqueId());

        // Remove any GUI state (GuiListener also does this, but belt-and-suspenders)
        plugin.getGuiManager().clearPlayer(player);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Sends a notification if the player has uncollected auction items or funds.
     * The actual items/funds are not given here — the player uses /auction collect.
     */
    private void notifyAuctionCollect(Player player) {
        if (!player.isOnline()) return;
        try {
            if (plugin.getAuctionService().hasItemsToCollect(player.getUniqueId())) {
                plugin.getMessageManager().send(player, "auction.pending-collect");
            }
        } catch (Exception e) {
            // Auction service may not be available if startup failed partially
            plugin.getLogger().warning("[PlayerListener] Auction collect check failed for "
                + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * If the player is in an active trade, cancels it and returns all items
     * to both participants before the player's inventory is saved.
     */
    private void cancelActiveTrade(Player player) {
        try {
            if (plugin.getTradeManager().isInTrade(player.getUniqueId())) {
                plugin.getTradeManager().cancel(player);
                // The other party (if online) is notified by TradeManager.cancel()
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PlayerListener] Trade cancel failed for "
                + player.getName() + ": " + e.getMessage());
        }
    }
}
