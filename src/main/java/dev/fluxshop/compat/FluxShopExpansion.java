package dev.fluxshop.compat;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.economy.EconomyProvider;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI expansion — registers all {@code %fluxshop_*%} placeholders.
 *
 * <h3>Complete placeholder list</h3>
 * <b>Economy</b>
 * <ul>
 *   <li>{@code %fluxshop_balance%}                — formatted balance (default economy)</li>
 *   <li>{@code %fluxshop_balance_raw%}             — raw double balance</li>
 *   <li>{@code %fluxshop_balance_<provider>%}      — formatted balance for a specific provider</li>
 *   <li>{@code %fluxshop_currency%}                — currency display name</li>
 *   <li>{@code %fluxshop_currency_plural%}         — currency plural name</li>
 *   <li>{@code %fluxshop_currency_symbol%}         — currency symbol</li>
 *   <li>{@code %fluxshop_discount%}                — player's active buy discount %</li>
 *   <li>{@code %fluxshop_sell_multiplier%}         — player's active sell multiplier</li>
 * </ul>
 * <b>Shops</b>
 * <ul>
 *   <li>{@code %fluxshop_shop_count%}              — number of loaded shops</li>
 *   <li>{@code %fluxshop_shop_<id>_item_count%}    — items in a specific shop</li>
 *   <li>{@code %fluxshop_shop_<id>_categories%}    — category count in a shop</li>
 * </ul>
 * <b>Auction House</b>
 * <ul>
 *   <li>{@code %fluxshop_auction_count%}           — global active listing count</li>
 *   <li>{@code %fluxshop_player_auction_count%}    — player's own listing count</li>
 *   <li>{@code %fluxshop_has_collect%}             — true/false — player has items to collect</li>
 * </ul>
 * <b>Black Market</b>
 * <ul>
 *   <li>{@code %fluxshop_blackmarket_time%}        — time until next BM rotation (HH:MM:SS)</li>
 *   <li>{@code %fluxshop_blackmarket_items%}       — number of current BM listings</li>
 * </ul>
 * <b>Trade</b>
 * <ul>
 *   <li>{@code %fluxshop_in_trade%}                — true/false — player is in an active trade</li>
 * </ul>
 * <b>Analytics (cached, refreshed every 5 min)</b>
 * <ul>
 *   <li>{@code %fluxshop_top_item%}                — most purchased item name (24h)</li>
 *   <li>{@code %fluxshop_top_buyer%}               — most active buyer name (24h)</li>
 *   <li>{@code %fluxshop_transactions_24h%}        — transaction count in last 24 hours</li>
 * </ul>
 * <b>Meta</b>
 * <ul>
 *   <li>{@code %fluxshop_version%}                 — plugin version string</li>
 * </ul>
 */
public class FluxShopExpansion extends PlaceholderExpansion {

    private final FluxShopPlugin plugin;

    // ── Analytics cache (refreshed asynchronously every 5 minutes) ────────────
    private volatile String cachedTopItem    = "N/A";
    private volatile String cachedTopBuyer   = "N/A";
    private volatile long   cachedTxCount24h = 0;
    private volatile Instant lastRefresh     = Instant.EPOCH;
    private static final long CACHE_TTL_SECS = 300; // 5 minutes

    // ── Discount/multiplier cache per player (short TTL) ──────────────────────
    private final Map<UUID, double[]> discountCache = new ConcurrentHashMap<>();
    // double[] = {discountPct, sellMultiplier, expireEpochSec}

    public FluxShopExpansion(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "fluxshop"; }
    @Override public @NotNull String getAuthor()     { return "sneakywrld"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean         persist()       { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        String p = params.toLowerCase();

        // ── Economy ───────────────────────────────────────────────────────────
        var defaultEco = plugin.getEconomyRegistry().getDefault();

        if (p.equals("balance")) {
            return defaultEco != null
                ? defaultEco.format(defaultEco.getBalance(player.getUniqueId()))
                : "0";
        }
        if (p.equals("balance_raw")) {
            return defaultEco != null
                ? String.format("%.2f", defaultEco.getBalance(player.getUniqueId()))
                : "0.00";
        }
        if (p.startsWith("balance_")) {
            String providerId = p.substring("balance_".length());
            EconomyProvider eco = plugin.getEconomyRegistry().get(providerId);
            return eco != null
                ? eco.format(eco.getBalance(player.getUniqueId()))
                : "0";
        }
        if (p.equals("currency"))        return defaultEco != null ? defaultEco.getCurrencyName()       : "Unknown";
        if (p.equals("currency_plural"))  return defaultEco != null ? defaultEco.getCurrencyNamePlural() : "Unknown";
        if (p.equals("currency_symbol"))  return defaultEco != null ? defaultEco.getCurrencySymbol()     : "";

        if (p.equals("discount")) {
            double[] cached = getDiscountData(player);
            return String.format("%.1f", cached[0]);
        }
        if (p.equals("sell_multiplier")) {
            double[] cached = getDiscountData(player);
            return String.format("%.2f", cached[1]);
        }

        // ── Shops ─────────────────────────────────────────────────────────────
        if (p.equals("shop_count")) return String.valueOf(plugin.getShopManager().getShopCount());

        if (p.startsWith("shop_") && p.endsWith("_item_count")) {
            String shopId = p.substring("shop_".length(), p.length() - "_item_count".length());
            return plugin.getShopManager().getShop(shopId)
                .map(s -> s.getCategories().stream()
                    .mapToInt(c -> c.getItems().size()).sum())
                .map(String::valueOf)
                .orElse("0");
        }
        if (p.startsWith("shop_") && p.endsWith("_categories")) {
            String shopId = p.substring("shop_".length(), p.length() - "_categories".length());
            return plugin.getShopManager().getShop(shopId)
                .map(s -> String.valueOf(s.getCategories().size()))
                .orElse("0");
        }

        // ── Auction House ─────────────────────────────────────────────────────
        if (p.equals("auction_count")) {
            return String.valueOf(plugin.getAuctionService().getActiveListings().size());
        }
        if (p.equals("player_auction_count")) {
            long count = plugin.getAuctionService().getActiveListings().stream()
                .filter(l -> l.getSellerUuid().equals(player.getUniqueId()))
                .count();
            return String.valueOf(count);
        }
        if (p.equals("has_collect")) {
            return String.valueOf(plugin.getAuctionService().hasItemsToCollect(player.getUniqueId()));
        }

        // ── Black Market ──────────────────────────────────────────────────────
        if (p.equals("blackmarket_time")) {
            Instant next = plugin.getBlackMarketService().getNextRotation();
            if (next == null) return "N/A";
            long secs = Instant.now().until(next, ChronoUnit.SECONDS);
            if (secs <= 0) return "00:00:00";
            long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
            return String.format("%02d:%02d:%02d", h, m, s);
        }
        if (p.equals("blackmarket_items")) {
            return String.valueOf(plugin.getBlackMarketService().getCurrentListings().size());
        }

        // ── Trade ─────────────────────────────────────────────────────────────
        if (p.equals("in_trade")) {
            return String.valueOf(plugin.getTradeManager().isInTrade(player.getUniqueId()));
        }

        // ── Analytics (cached) ────────────────────────────────────────────────
        refreshAnalyticsIfStale();
        if (p.equals("top_item"))          return cachedTopItem;
        if (p.equals("top_buyer"))         return cachedTopBuyer;
        if (p.equals("transactions_24h"))  return String.valueOf(cachedTxCount24h);

        // ── Meta ──────────────────────────────────────────────────────────────
        if (p.equals("version")) return plugin.getDescription().getVersion();

        return null; // unknown placeholder — PAPI will leave it unreplaced
    }

    // ── Discount / multiplier helper ──────────────────────────────────────────

    /**
     * Returns cached {@code [discountPct, sellMultiplier]} for the player.
     * Reads permission groups defined in config.yml.
     * Cache TTL: 30 seconds to avoid spamming hasPermission on every placeholder request.
     */
    private double[] getDiscountData(Player player) {
        long now = System.currentTimeMillis() / 1000;
        double[] cached = discountCache.get(player.getUniqueId());
        if (cached != null && cached[2] > now) return cached;

        // Recalculate
        double maxDiscount    = 0;
        double maxMultiplier  = 1.0;

        var discountSection = plugin.getConfigManager().getConfig()
            .getConfigurationSection("discounts.groups");
        if (discountSection != null && plugin.getConfigManager().getBoolean("discounts.enabled", true)) {
            for (String key : discountSection.getKeys(false)) {
                if (player.hasPermission("fluxshop.discount." + key)) {
                    maxDiscount = Math.max(maxDiscount,
                        discountSection.getDouble(key, 0));
                }
            }
        }

        var multSection = plugin.getConfigManager().getConfig()
            .getConfigurationSection("sell-multipliers.groups");
        if (multSection != null && plugin.getConfigManager().getBoolean("sell-multipliers.enabled", true)) {
            for (String key : multSection.getKeys(false)) {
                if (player.hasPermission("fluxshop.multiplier." + key)) {
                    maxMultiplier = Math.max(maxMultiplier,
                        multSection.getDouble(key, 1.0));
                }
            }
        }

        double[] result = { maxDiscount, maxMultiplier, now + 30 };
        discountCache.put(player.getUniqueId(), result);
        return result;
    }

    // ── Analytics cache refresh ───────────────────────────────────────────────

    private void refreshAnalyticsIfStale() {
        if (Instant.now().isBefore(lastRefresh.plusSeconds(CACHE_TTL_SECS))) return;
        lastRefresh = Instant.now();

        long windowMs = 24L * 60 * 60 * 1000;

        plugin.getAnalyticsRepository().getTopBought(windowMs, 1).thenAccept(list -> {
            if (list != null && !list.isEmpty() && list.get(0).itemName() != null) cachedTopItem = list.get(0).itemName();
        });
        plugin.getAnalyticsRepository().getTopBuyers(windowMs, 1).thenAccept(list -> {
            if (list != null && !list.isEmpty() && list.get(0).playerName() != null) cachedTopBuyer = list.get(0).playerName();
        });
        plugin.getAnalyticsRepository().getTransactionCount(windowMs).thenAccept(count -> {
            if (count != null) cachedTxCount24h = count;
        });
    }
}
