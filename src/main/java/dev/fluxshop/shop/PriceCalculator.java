package dev.fluxshop.shop;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.ShopItem;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Calculates the effective buy and sell price for a {@link ShopItem} and player.
 *
 * <p>Price modifiers are applied in this order:
 * <ol>
 *   <li>Base price from item config</li>
 *   <li>Dynamic pricing multiplier (supply/demand)</li>
 *   <li>Seasonal modifier (RealisticSeasons)</li>
 *   <li>Per-player modifier (admin-set)</li>
 *   <li>Permission discount/multiplier (LuckPerms groups)</li>
 * </ol>
 */
public class PriceCalculator {

    private final FluxShopPlugin plugin;

    public PriceCalculator(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the effective buy price for a player, or -1 if the item is not buyable.
     */
    public double getBuyPrice(ShopItem item, Player player, int quantity) {
        if (!item.isBuyable()) return -1;
        double base = item.getBuyPrice();
        base *= getDynamicMultiplier(item);
        base *= getSeasonalMultiplier(item);
        base *= getPlayerModifier(item, player);
        base *= getBuyDiscountMultiplier(player);
        return round(base * quantity);
    }

    /**
     * Returns the effective sell price for a player, or -1 if the item is not sellable.
     */
    public double getSellPrice(ShopItem item, Player player, int quantity) {
        if (!item.isSellable()) return -1;
        double base = item.getSellPrice();
        base *= getSeasonalMultiplier(item);
        base *= getPlayerModifier(item, player);
        base *= getSellMultiplier(player);
        return round(base * quantity);
    }

    // ── Individual modifiers ──────────────────────────────────────────────────

    private double getDynamicMultiplier(ShopItem item) {
        if (!item.isDynamicPricingEnabled()) return 1.0;
        if (!plugin.getConfigManager().getBoolean("dynamic-pricing.enabled", false)) return 1.0;
        return item.getDynamicMultiplier();
    }

    private double getSeasonalMultiplier(ShopItem item) {
        if (!plugin.getConfigManager().getBoolean("realistic-seasons.enabled", true)) return 1.0;
        String season = plugin.getCompatManager().getCurrentSeason();
        if (season == null) return 1.0;

        // Item-specific season modifier overrides global config
        if (!item.getSeasonModifiers().isEmpty()) {
            return item.getSeasonModifiers().getOrDefault(season.toUpperCase(), 1.0);
        }
        return plugin.getConfigManager().getDouble("realistic-seasons.modifiers." + season.toUpperCase(), 1.0);
    }

    /**
     * Returns the player's personal price modifier for this item (set by admin).
     * Stored in the DB; 1.0 = no modifier.
     */
    private double getPlayerModifier(ShopItem item, Player player) {
        return plugin.getPriceModifierRepository().getModifier(player.getUniqueId(), item.getId());
    }

    /**
     * Returns buy discount based on permission groups.
     * E.g. player has {@code fluxshop.discount.vip} → 5% off → multiplier 0.95
     */
    private double getBuyDiscountMultiplier(Player player) {
        if (!plugin.getConfigManager().getBoolean("discounts.enabled", true)) return 1.0;
        Map<String, Object> groups = plugin.getConfigManager().getConfig()
            .getConfigurationSection("discounts.groups") != null
            ? plugin.getConfigManager().getConfig().getConfigurationSection("discounts.groups").getValues(false)
            : Map.of();
        double maxDiscount = 0;
        for (Map.Entry<String, Object> entry : groups.entrySet()) {
            if (player.hasPermission("fluxshop.discount." + entry.getKey())) {
                double discount = ((Number) entry.getValue()).doubleValue();
                maxDiscount = Math.max(maxDiscount, discount);
            }
        }
        return 1.0 - (maxDiscount / 100.0);
    }

    /**
     * Returns sell price multiplier based on permission groups.
     * E.g. VIP gets 1.10x sell price.
     */
    private double getSellMultiplier(Player player) {
        if (!plugin.getConfigManager().getBoolean("sell-multipliers.enabled", true)) return 1.0;
        Map<String, Object> groups = plugin.getConfigManager().getConfig()
            .getConfigurationSection("sell-multipliers.groups") != null
            ? plugin.getConfigManager().getConfig().getConfigurationSection("sell-multipliers.groups").getValues(false)
            : Map.of();
        double maxMultiplier = 1.0;
        for (Map.Entry<String, Object> entry : groups.entrySet()) {
            if (player.hasPermission("fluxshop.multiplier." + entry.getKey())) {
                double mult = ((Number) entry.getValue()).doubleValue();
                maxMultiplier = Math.max(maxMultiplier, mult);
            }
        }
        return maxMultiplier;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
