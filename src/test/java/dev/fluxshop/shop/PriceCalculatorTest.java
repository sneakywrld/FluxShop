package dev.fluxshop.shop;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.compat.CompatManager;
import dev.fluxshop.config.ConfigManager;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.storage.PriceModifierRepository;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceCalculatorTest {

    @Mock FluxShopPlugin           plugin;
    @Mock ConfigManager            configManager;
    @Mock CompatManager            compatManager;
    @Mock PriceModifierRepository  priceModifierRepository;
    @Mock Player                   player;

    PriceCalculator calculator;

    @BeforeEach
    void setUp() {
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(plugin.getCompatManager()).thenReturn(compatManager);
        when(plugin.getPriceModifierRepository()).thenReturn(priceModifierRepository);
        // Default: no player modifier active (use any() to also match null UUID from mock player)
        when(priceModifierRepository.getModifier(any(), anyString())).thenReturn(1.0);

        // Disable optional modifiers by default
        when(configManager.getBoolean("dynamic-pricing.enabled", false)).thenReturn(false);
        when(configManager.getBoolean("realistic-seasons.enabled", true)).thenReturn(false);
        when(configManager.getBoolean("discounts.enabled", true)).thenReturn(false);
        when(configManager.getBoolean("sell-multipliers.enabled", true)).thenReturn(false);

        calculator = new PriceCalculator(plugin);
    }

    // ── isBuyable / isSellable guards ─────────────────────────────────────────

    @Test
    void getBuyPrice_notBuyable_returnsNegativeOne() {
        ShopItem item = item(-1, 10.0); // buyPrice=-1 → not buyable
        assertEquals(-1.0, calculator.getBuyPrice(item, player, 1));
    }

    @Test
    void getSellPrice_notSellable_returnsNegativeOne() {
        ShopItem item = item(10.0, -1); // sellPrice=-1 → not sellable
        assertEquals(-1.0, calculator.getSellPrice(item, player, 1));
    }

    // ── Basic price × quantity ────────────────────────────────────────────────

    @Test
    void getBuyPrice_basePrice_multipliedByQuantity() {
        ShopItem item = item(5.0, 2.0);
        assertEquals(25.0, calculator.getBuyPrice(item, player, 5));
    }

    @Test
    void getSellPrice_basePrice_multipliedByQuantity() {
        ShopItem item = item(10.0, 3.0);
        assertEquals(9.0, calculator.getSellPrice(item, player, 3));
    }

    // ── Rounding ──────────────────────────────────────────────────────────────

    @Test
    void getBuyPrice_roundedToTwoDecimalPlaces() {
        ShopItem item = item(1.005, 0); // 1.005 × 3 = 3.015 → 3.02
        double result = calculator.getBuyPrice(item, player, 3);
        // Result should be rounded to 2dp
        assertEquals(result, Math.round(result * 100.0) / 100.0, 0.001);
    }

    // ── Dynamic pricing ───────────────────────────────────────────────────────

    @Test
    void getBuyPrice_dynamicPricingEnabled_appliesMultiplier() {
        when(configManager.getBoolean("dynamic-pricing.enabled", false)).thenReturn(true);

        ShopItem item = item(100.0, 50.0);
        item.setDynamicPricingEnabled(true);
        item.setDynamicMultiplier(1.5); // 50% more expensive

        assertEquals(150.0, calculator.getBuyPrice(item, player, 1));
    }

    @Test
    void getBuyPrice_dynamicPricingDisabledInConfig_ignoresItemMultiplier() {
        when(configManager.getBoolean("dynamic-pricing.enabled", false)).thenReturn(false);

        ShopItem item = item(100.0, 50.0);
        item.setDynamicPricingEnabled(true);
        item.setDynamicMultiplier(2.0); // would double price — but config disabled

        assertEquals(100.0, calculator.getBuyPrice(item, player, 1));
    }

    // ── Seasonal pricing ──────────────────────────────────────────────────────

    @Test
    void getBuyPrice_seasonalModifierApplied_forCurrentSeason() {
        when(configManager.getBoolean("realistic-seasons.enabled", true)).thenReturn(true);
        when(compatManager.getCurrentSeason()).thenReturn("SUMMER");

        ShopItem item = item(100.0, 50.0);
        item.setSeasonModifiers(Map.of("SUMMER", 1.25)); // 25% more expensive in summer

        assertEquals(125.0, calculator.getBuyPrice(item, player, 1));
    }

    @Test
    void getSellPrice_seasonalModifierApplied_forCurrentSeason() {
        when(configManager.getBoolean("realistic-seasons.enabled", true)).thenReturn(true);
        when(compatManager.getCurrentSeason()).thenReturn("WINTER");

        ShopItem item = item(100.0, 80.0);
        item.setSeasonModifiers(Map.of("WINTER", 0.8)); // 20% less in winter

        assertEquals(64.0, calculator.getSellPrice(item, player, 1));
    }

    @Test
    void getBuyPrice_unknownSeason_noModifier() {
        when(configManager.getBoolean("realistic-seasons.enabled", true)).thenReturn(true);
        when(compatManager.getCurrentSeason()).thenReturn("SPRING");
        when(configManager.getDouble("realistic-seasons.modifiers.SPRING", 1.0)).thenReturn(1.0);

        ShopItem item = item(100.0, 50.0);
        // No item-level season modifier, falls back to config which returns 1.0

        assertEquals(100.0, calculator.getBuyPrice(item, player, 1));
    }

    // ── Discount permissions ──────────────────────────────────────────────────

    @Test
    void getBuyPrice_withDiscount_reducesPrice() {
        when(configManager.getBoolean("discounts.enabled", true)).thenReturn(true);

        // Config returns discount groups: vip=10 (10% off)
        YamlConfiguration config = new YamlConfiguration();
        config.set("discounts.groups.vip", 10);
        when(configManager.getConfig()).thenReturn(config);
        when(player.hasPermission("fluxshop.discount.vip")).thenReturn(true);

        ShopItem item = item(100.0, 50.0);
        // 100 × 0.90 = 90.0
        assertEquals(90.0, calculator.getBuyPrice(item, player, 1));
    }

    @Test
    void getBuyPrice_bestDiscountWins_whenMultipleGroups() {
        when(configManager.getBoolean("discounts.enabled", true)).thenReturn(true);

        YamlConfiguration config = new YamlConfiguration();
        config.set("discounts.groups.vip", 10);
        config.set("discounts.groups.mvp", 20);
        when(configManager.getConfig()).thenReturn(config);
        when(player.hasPermission("fluxshop.discount.vip")).thenReturn(true);
        when(player.hasPermission("fluxshop.discount.mvp")).thenReturn(true);

        ShopItem item = item(100.0, 50.0);
        // Max discount = 20% → 100 × 0.80 = 80.0
        assertEquals(80.0, calculator.getBuyPrice(item, player, 1));
    }

    // ── Sell multipliers ──────────────────────────────────────────────────────

    @Test
    void getSellPrice_withMultiplier_increasesEarnings() {
        when(configManager.getBoolean("sell-multipliers.enabled", true)).thenReturn(true);

        YamlConfiguration config = new YamlConfiguration();
        config.set("sell-multipliers.groups.vip", 1.5);
        when(configManager.getConfig()).thenReturn(config);
        when(player.hasPermission("fluxshop.multiplier.vip")).thenReturn(true);

        ShopItem item = item(100.0, 50.0);
        // 50 × 1.5 = 75.0
        assertEquals(75.0, calculator.getSellPrice(item, player, 1));
    }

    // ── Quantity edge cases ───────────────────────────────────────────────────

    @Test
    void getBuyPrice_quantityOne_returnsSingleUnitPrice() {
        ShopItem item = item(99.99, 0);
        assertEquals(99.99, calculator.getBuyPrice(item, player, 1));
    }

    @Test
    void getBuyPrice_quantity64_returnsCorrectTotal() {
        ShopItem item = item(1.0, 0);
        assertEquals(64.0, calculator.getBuyPrice(item, player, 64));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ShopItem item(double buyPrice, double sellPrice) {
        ShopItem i = new ShopItem();
        i.setId("test-item");
        i.setShopId("test-shop");
        i.setCategoryId("test-cat");
        i.setBuyPrice(buyPrice);
        i.setSellPrice(sellPrice);
        return i;
    }
}
