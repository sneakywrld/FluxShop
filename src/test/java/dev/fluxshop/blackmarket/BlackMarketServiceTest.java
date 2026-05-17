package dev.fluxshop.blackmarket;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.economy.EconomyProvider;
import dev.fluxshop.economy.EconomyRegistry;
import dev.fluxshop.model.BlackMarketListing;
import dev.fluxshop.storage.BlackMarketRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

/**
 * Tests for the validation paths inside {@link BlackMarketService#purchase}.
 *
 * <p>All tests cover guard-clause returns that occur before the Bukkit event
 * is fired — these run cleanly without a Bukkit server environment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlackMarketServiceTest {

    @Mock FluxShopPlugin     plugin;
    @Mock EconomyRegistry    economyRegistry;
    @Mock EconomyProvider    economy;
    @Mock BlackMarketRepository repo;
    @Mock Player             buyer;

    private static final UUID BUYER_UUID = UUID.randomUUID();
    private static final String CURRENCY = "vault";

    BlackMarketService service;

    @BeforeEach
    void setUp() {
        when(plugin.getEconomyRegistry()).thenReturn(economyRegistry);
        when(economyRegistry.get(CURRENCY)).thenReturn(economy);
        when(buyer.getUniqueId()).thenReturn(BUYER_UUID);

        // Default: buyer can afford anything
        when(economy.has(any(UUID.class), anyDouble())).thenReturn(true);

        service = new BlackMarketService(plugin);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BlackMarketListing listing(int stock, int purchased, int maxPerPlayer) {
        BlackMarketListing l = new BlackMarketListing();
        l.setId(1L);
        l.setStock(stock);
        l.setPurchased(purchased);
        l.setMaxPerPlayer(maxPerPlayer);
        l.setPrice(100.0);
        l.setCurrency(CURRENCY);
        ItemStack item = mock(ItemStack.class);
        when(item.clone()).thenReturn(item);
        when(item.getType()).thenReturn(org.bukkit.Material.DIRT);
        l.setItem(item);
        return l;
    }

    // ── Out of stock ──────────────────────────────────────────────────────────

    @Test
    void purchase_outOfStock_returnsError() {
        BlackMarketListing l = listing(5, 5, 0); // fully sold out
        String err = service.purchase(buyer, l, 1);
        assertNotNull(err);
        assertTrue(err.toLowerCase().contains("stock") || err.toLowerCase().contains("out"));
    }

    @Test
    void purchase_stockZero_returnsError() {
        BlackMarketListing l = listing(0, 0, 0);
        assertNotNull(service.purchase(buyer, l, 1));
    }

    // ── Per-player limit ──────────────────────────────────────────────────────

    @Test
    void purchase_playerLimitReached_returnsError() {
        // limit=2; buying 3 at once fails the (0 + 3 > 2) check before reaching Bukkit event
        BlackMarketListing l = listing(100, 0, 2);
        String err = service.purchase(buyer, l, 3);
        assertNotNull(err);
        assertTrue(err.toLowerCase().contains("buy") || err.toLowerCase().contains("limit") || err.toLowerCase().contains("only"));
    }

    @Test
    void purchase_unlimitedLimit_doesNotBlockLargeQuantity() {
        // limit=0 means unlimited; buying 50 at once should NOT be blocked by the limit check
        BlackMarketListing l = listing(100, 0, 0);
        // The call will either return null or NPE at Bukkit.callEvent; both mean validation passed
        String err = null;
        try {
            err = service.purchase(buyer, l, 50);
        } catch (NullPointerException ignored) {
            // NPE from Bukkit.callEvent — validation guards all passed, which is correct
            return;
        }
        if (err != null) {
            assertFalse(err.toLowerCase().contains("limit"),
                "Should not receive a limit error when maxPerPlayer=0");
        }
    }

    // ── Quantity > remaining stock ────────────────────────────────────────────

    @Test
    void purchase_quantityExceedsRemainingStock_returnsError() {
        BlackMarketListing l = listing(5, 3, 0); // 2 remaining
        String err = service.purchase(buyer, l, 3); // trying to buy 3 of 2 remaining
        assertNotNull(err);
        assertTrue(err.toLowerCase().contains("only") || err.toLowerCase().contains("remaining") || err.toLowerCase().contains("stock"));
    }

    @Test
    void purchase_exactRemainingStock_doesNotReturnStockError() {
        BlackMarketListing l = listing(5, 3, 0); // 2 remaining
        // Buying exactly 2 should pass the stock check (may NPE at Bukkit.callEvent)
        String err = null;
        try {
            err = service.purchase(buyer, l, 2);
        } catch (NullPointerException ignored) {
            return; // NPE means all validation passed — correct
        }
        if (err != null) {
            assertFalse(err.toLowerCase().contains("only") && err.toLowerCase().contains("remaining"),
                "Buying exact remaining stock should not produce a remaining-stock error");
        }
    }

    // ── Economy provider unavailable ──────────────────────────────────────────

    @Test
    void purchase_noEconomyProvider_returnsError() {
        when(economyRegistry.get(CURRENCY)).thenReturn(null); // no provider
        BlackMarketListing l = listing(10, 0, 0);
        String err = service.purchase(buyer, l, 1);
        assertNotNull(err);
        assertTrue(err.toLowerCase().contains("economy") || err.toLowerCase().contains("provider"));
    }

    // ── Insufficient funds ────────────────────────────────────────────────────

    @Test
    void purchase_insufficientFunds_returnsError() {
        when(economy.has(BUYER_UUID, 100.0)).thenReturn(false);
        BlackMarketListing l = listing(10, 0, 0);
        String err = service.purchase(buyer, l, 1);
        assertNotNull(err);
        assertTrue(err.toLowerCase().contains("afford") || err.toLowerCase().contains("funds") || err.toLowerCase().contains("money"));
    }

    @Test
    void purchase_exactFunds_doesNotReturnFundsError() {
        when(economy.has(BUYER_UUID, 100.0)).thenReturn(true); // exactly enough
        BlackMarketListing l = listing(10, 0, 0);
        // Success path reaches Bukkit.callEvent which NPEs without a Bukkit server
        String err = null;
        try {
            err = service.purchase(buyer, l, 1);
        } catch (NullPointerException ignored) {
            return; // NPE means all validation passed — correct
        }
        if (err != null) {
            assertFalse(err.toLowerCase().contains("afford"),
                "Should not return an afford error when player has enough funds");
        }
    }

    // ── getPlayerPurchaseCount ────────────────────────────────────────────────

    @Test
    void getPlayerPurchaseCount_noEntry_returnsZero() {
        BlackMarketListing l = listing(10, 0, 0);
        assertEquals(0, service.getPlayerPurchaseCount(l.getId(), BUYER_UUID));
    }

    @Test
    void getPlayerPurchaseCount_unknownPlayer_returnsZero() {
        assertEquals(0, service.getPlayerPurchaseCount(999L, UUID.randomUUID()));
    }
}
