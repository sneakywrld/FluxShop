package dev.fluxshop.compat;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.AuctionListing;
import dev.fluxshop.model.Transaction;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Sends shop and auction events to a Discord channel via DiscordSRV's webhook API.
 *
 * <p>All DiscordSRV calls are made via reflection so there's no compile-time
 * dependency on the DiscordSRV JAR.
 */
public class DiscordLogger {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final FluxShopPlugin plugin;
    private final String         channelName;  // DiscordSRV channel key from config

    public DiscordLogger(FluxShopPlugin plugin) {
        this.plugin      = plugin;
        this.channelName = plugin.getConfigManager().getString(
            "integrations.discordsrv.channel", "global");
    }

    // ── Public logging methods ────────────────────────────────────────────────

    /** Logs a buy or sell transaction. */
    public void logTransaction(Player player, Transaction tx) {
        if (!isEnabled()) return;
        String type = tx.getType() == Transaction.Type.BUY ? "🛒 Purchase" : "💰 Sale";
        String eco  = plugin.getEconomyRegistry().get(tx.getCurrency()) != null
            ? plugin.getEconomyRegistry().get(tx.getCurrency()).format(tx.getTotalPrice())
            : "$" + tx.getTotalPrice();
        String msg = String.format("**%s** — %s\n> Item: `%s` ×%d\n> Price: %s\n> Shop: `%s`\n> Time: %s",
            type, player.getName(),
            tx.getItemId(), tx.getQuantity(),
            eco,
            tx.getShopId(),
            FMT.format(tx.getTimestamp()));
        sendMessage(msg);
    }

    /** Logs a new auction listing. */
    public void logAuctionList(Player seller, AuctionListing listing) {
        if (!isEnabled()) return;
        dev.fluxshop.economy.EconomyProvider ecoProvider = plugin.getEconomyRegistry().get(listing.getCurrency());
        String eco = ecoProvider != null
            ? ecoProvider.format(listing.getStartPrice()) : "$" + listing.getStartPrice();
        String buyNow = listing.hasBuyNow()
            ? " | Buy-Now: " + (ecoProvider != null
                ? ecoProvider.format(listing.getBuyNowPrice())
                : "$" + listing.getBuyNowPrice())
            : "";
        String msg = String.format("**🔨 Auction Listed** — %s\n> Item: `%s`\n> Start: %s%s\n> Expires: %s",
            seller.getName(),
            listing.getItem() != null ? listing.getItem().getType().name() : "Unknown",
            eco, buyNow,
            FMT.format(listing.getExpiresAt()));
        sendMessage(msg);
    }

    /** Logs a completed auction sale. */
    public void logAuctionSold(AuctionListing listing, String buyerName, double finalPrice) {
        if (!isEnabled()) return;
        String eco = plugin.getEconomyRegistry().get(listing.getCurrency()) != null
            ? plugin.getEconomyRegistry().get(listing.getCurrency()).format(finalPrice) : "$" + finalPrice;
        String msg = String.format("**🏆 Auction Sold** — %s → %s\n> Item: `%s`\n> Final Price: %s",
            listing.getSellerName(), buyerName,
            listing.getItem() != null ? listing.getItem().getType().name() : "Unknown",
            eco);
        sendMessage(msg);
    }

    /** Logs a new bid placed on a listing. */
    public void logAuctionBid(Player bidder, AuctionListing listing, double amount) {
        if (!isEnabled()) return;
        String eco = plugin.getEconomyRegistry().get(listing.getCurrency()) != null
            ? plugin.getEconomyRegistry().get(listing.getCurrency()).format(amount) : "$" + amount;
        String msg = String.format("**📈 Bid Placed** — %s\n> Item: `%s`\n> Bid: %s",
            bidder.getName(),
            listing.getItem() != null ? listing.getItem().getType().name() : "Unknown",
            eco);
        sendMessage(msg);
    }

    public void logBlackMarket(org.bukkit.entity.Player buyer, dev.fluxshop.model.BlackMarketListing listing, int qty, double price) {
        if (!isEnabled()) return;
        List<String> events = plugin.getConfigManager().getStringList("discord.log-events");
        if (!events.contains("BLACK_MARKET")) return;
        dev.fluxshop.economy.EconomyProvider eco = plugin.getEconomyRegistry().get(listing.getCurrency());
        String priceStr = eco != null ? eco.format(price) : "$" + price;
        String msg = String.format("**🖤 Black Market Purchase** — %s\n> Item: `%s` x%d\n> Price: %s",
            buyer.getName(),
            listing.getItem() != null ? listing.getItem().getType().name() : "Unknown",
            qty, priceStr);
        sendMessage(msg);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private boolean isEnabled() {
        return plugin.getCompatManager().hasDiscordSRV()
            && plugin.getConfigManager().getBoolean("integrations.discordsrv.enabled", true);
    }

    /**
     * Sends a plain-text message to the configured DiscordSRV channel.
     * Uses reflection against the DiscordSRV static API.
     */
    private void sendMessage(String message) {
        // Run off the main thread — DiscordSRV sends are async but we're defensive
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // DiscordSRV.getPlugin().sendMessage(channelName, message)
                Class<?> cls      = Class.forName("github.scarsz.discordsrv.DiscordSRV");
                Object   instance = cls.getMethod("getPlugin").invoke(null);
                Method   send     = cls.getMethod("sendMessage", String.class, String.class);
                send.invoke(instance, channelName, message);
            } catch (Exception ignored) {
                // DiscordSRV not fully loaded yet, or API changed — fail silently
            }
        });
    }
}
