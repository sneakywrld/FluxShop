package dev.fluxshop.blackmarket;

import dev.fluxshop.model.BlackMarketListing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BlackMarketListing} stock and limit helpers.
 */
class BlackMarketListingTest {

    private BlackMarketListing listing(int stock, int purchased, int maxPerPlayer) {
        BlackMarketListing l = new BlackMarketListing();
        l.setStock(stock);
        l.setPurchased(purchased);
        l.setMaxPerPlayer(maxPerPlayer);
        l.setPrice(100.0);
        l.setCurrency("vault");
        return l;
    }

    // ── getRemainingStock ─────────────────────────────────────────────────────

    @Test
    void getRemainingStock_noPurchases_equalsStock() {
        BlackMarketListing l = listing(10, 0, 0);
        assertEquals(10, l.getRemainingStock());
    }

    @Test
    void getRemainingStock_somePurchases_stockMinusPurchased() {
        BlackMarketListing l = listing(10, 3, 0);
        assertEquals(7, l.getRemainingStock());
    }

    @Test
    void getRemainingStock_allSold_returnsZero() {
        BlackMarketListing l = listing(10, 10, 0);
        assertEquals(0, l.getRemainingStock());
    }

    @Test
    void getRemainingStock_purchasedExceedsStock_clampsToZero() {
        // Should not happen in practice but the model should be defensive
        BlackMarketListing l = listing(5, 7, 0);
        assertEquals(0, l.getRemainingStock());
    }

    // ── isInStock ─────────────────────────────────────────────────────────────

    @Test
    void isInStock_remainingStockPositive_returnsTrue() {
        BlackMarketListing l = listing(10, 5, 0);
        assertTrue(l.isInStock());
    }

    @Test
    void isInStock_fullyPurchased_returnsFalse() {
        BlackMarketListing l = listing(10, 10, 0);
        assertFalse(l.isInStock());
    }

    @Test
    void isInStock_stockZero_returnsFalse() {
        BlackMarketListing l = listing(0, 0, 0);
        assertFalse(l.isInStock());
    }

    @Test
    void isInStock_singleItemLeft_returnsTrue() {
        BlackMarketListing l = listing(5, 4, 0);
        assertTrue(l.isInStock());
    }

    // ── maxPerPlayer ─────────────────────────────────────────────────────────

    @Test
    void maxPerPlayer_zero_meansUnlimited() {
        BlackMarketListing l = listing(100, 0, 0);
        assertEquals(0, l.getMaxPerPlayer()); // 0 = unlimited by convention
    }

    @Test
    void maxPerPlayer_setToThree_returnsThree() {
        BlackMarketListing l = listing(100, 0, 3);
        assertEquals(3, l.getMaxPerPlayer());
    }

    // ── price / currency ──────────────────────────────────────────────────────

    @Test
    void price_setAndGet_returnsCorrectValue() {
        BlackMarketListing l = new BlackMarketListing();
        l.setPrice(1234.56);
        assertEquals(1234.56, l.getPrice(), 0.001);
    }

    @Test
    void currency_setAndGet_returnsCorrectValue() {
        BlackMarketListing l = new BlackMarketListing();
        l.setCurrency("flux_coins");
        assertEquals("flux_coins", l.getCurrency());
    }

    // ── purchased tracking ────────────────────────────────────────────────────

    @Test
    void purchased_incrementedCorrectly() {
        BlackMarketListing l = listing(10, 0, 0);
        l.setPurchased(l.getPurchased() + 1);
        assertEquals(1, l.getPurchased());
        assertEquals(9, l.getRemainingStock());
    }

    @Test
    void purchased_multipleIncrements_updatesStock() {
        BlackMarketListing l = listing(10, 0, 0);
        for (int i = 0; i < 5; i++) l.setPurchased(l.getPurchased() + 1);
        assertEquals(5, l.getPurchased());
        assertEquals(5, l.getRemainingStock());
        assertTrue(l.isInStock());
    }
}
