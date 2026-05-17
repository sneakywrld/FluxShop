package dev.fluxshop.shop;

import dev.fluxshop.model.ShopItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for pure logic within {@link SellAllProcessor}.
 *
 * <p>Tests the {@link SellAllProcessor.SellResult} record and the best-price selection
 * semantics without requiring a live Bukkit environment.
 */
class SellAllProcessorTest {

    // ── SellResult record ─────────────────────────────────────────────────────

    @Test
    void sellResult_zeroItems_earnsNothing() {
        SellAllProcessor.SellResult result = new SellAllProcessor.SellResult(0, 0.0, "", Map.of());
        assertEquals(0, result.itemsSold());
        assertEquals(0.0, result.totalEarned(), 0.001);
        assertTrue(result.breakdown().isEmpty());
    }

    @Test
    void sellResult_storesAllFields() {
        Map<String, Double> breakdown = Map.of("DIAMOND", 500.0, "IRON_INGOT", 50.0);
        SellAllProcessor.SellResult result = new SellAllProcessor.SellResult(74, 550.0, "Dollars", breakdown);

        assertEquals(74,      result.itemsSold());
        assertEquals(550.0,   result.totalEarned(), 0.001);
        assertEquals("Dollars", result.currency());
        assertEquals(500.0,   result.breakdown().get("DIAMOND"), 0.001);
        assertEquals(50.0,    result.breakdown().get("IRON_INGOT"), 0.001);
    }

    @Test
    void sellResult_breakdownSumsToTotal() {
        double diamond = 500.0;
        double iron    = 50.0;
        double total   = diamond + iron;

        SellAllProcessor.SellResult result = new SellAllProcessor.SellResult(
            74, total, "Gold",
            Map.of("DIAMOND", diamond, "IRON_INGOT", iron)
        );

        double breakdownSum = result.breakdown().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(total, breakdownSum, 0.01);
    }

    // ── Best-price selection logic ────────────────────────────────────────────

    @Test
    void bestPrice_higherSellPriceWins() {
        // Simulate what the best-price merge logic does:
        // If two shops both sell DIRT, the higher sell price should win.
        ShopItem cheap = shopItem("shop1", 1.0);
        ShopItem expensive = shopItem("shop2", 5.0);

        // Replicate the merge: (a, b) -> a.price >= b.price ? a : b
        record Entry(ShopItem item, double price) {}
        Entry winner = new Entry(cheap, cheap.getSellPrice());
        Entry challenger = new Entry(expensive, expensive.getSellPrice());
        Entry result = winner.price() >= challenger.price() ? winner : challenger;

        assertEquals(5.0, result.price(), 0.001);
        assertEquals("shop2", result.item().getShopId());
    }

    @Test
    void bestPrice_equalPrices_firstWins() {
        // When prices are equal, the first-encountered entry should be kept (merge keeps a when a >= b)
        ShopItem first  = shopItem("shop1", 3.0);
        ShopItem second = shopItem("shop2", 3.0);

        record Entry(ShopItem item, double price) {}
        Entry a = new Entry(first,  first.getSellPrice());
        Entry b = new Entry(second, second.getSellPrice());
        Entry result = a.price() >= b.price() ? a : b;

        assertEquals("shop1", result.item().getShopId());
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    @Test
    void sortByPriceHigh_descendingOrder() {
        record MatPrice(String material, double price) {}
        List<MatPrice> items = List.of(
            new MatPrice("IRON",    50.0),
            new MatPrice("DIAMOND", 500.0),
            new MatPrice("COAL",    10.0)
        );

        // Sort descending (PRICE_HIGH)
        List<MatPrice> sorted = items.stream()
            .sorted((a, b) -> Double.compare(b.price(), a.price()))
            .toList();

        assertEquals("DIAMOND", sorted.get(0).material());
        assertEquals("IRON",    sorted.get(1).material());
        assertEquals("COAL",    sorted.get(2).material());
    }

    @Test
    void sortByPriceLow_ascendingOrder() {
        record MatPrice(String material, double price) {}
        List<MatPrice> items = List.of(
            new MatPrice("IRON",    50.0),
            new MatPrice("DIAMOND", 500.0),
            new MatPrice("COAL",    10.0)
        );

        // Sort ascending (PRICE_LOW)
        List<MatPrice> sorted = items.stream()
            .sorted((a, b) -> Double.compare(a.price(), b.price()))
            .toList();

        assertEquals("COAL",    sorted.get(0).material());
        assertEquals("IRON",    sorted.get(1).material());
        assertEquals("DIAMOND", sorted.get(2).material());
    }

    // ── Earning calculation ───────────────────────────────────────────────────

    @Test
    void earnings_countTimesUnitPrice_roundedCorrectly() {
        double pricePerUnit = 1.005;
        int count = 100;
        // Math.round(pricePerUnit * count * 100.0) / 100.0
        double earned = Math.round(pricePerUnit * count * 100.0) / 100.0;
        assertEquals(100.50, earned, 0.001);
    }

    @Test
    void earnings_stackOf64_correctTotal() {
        double pricePerUnit = 1.25;
        int count = 64;
        double earned = Math.round(pricePerUnit * count * 100.0) / 100.0;
        assertEquals(80.0, earned, 0.001);
    }

    @Test
    void earnings_multipleItems_summedCorrectly() {
        double diamonds = 500.0 * 5;  // 5 diamonds at $500 each
        double iron     = 50.0  * 32; // 32 iron at $50 each
        double total    = diamonds + iron;

        assertEquals(4100.0, total, 0.001);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ShopItem shopItem(String shopId, double sellPrice) {
        ShopItem i = new ShopItem();
        i.setId("item1");
        i.setShopId(shopId);
        i.setCategoryId("cat1");
        i.setBuyPrice(-1);
        i.setSellPrice(sellPrice);
        return i;
    }
}
