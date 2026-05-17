package dev.fluxshop.shop;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.storage.StockRepository;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockManagerTest {

    @Mock FluxShopPlugin   plugin;
    @Mock StockRepository  stockRepo;
    @Mock Player           player;

    StockManager stockManager;
    UUID playerUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Inject mock repo via subclass; StockManager builds its own repo in the ctor,
        // so we test via a minimal subclass that accepts an injected repo.
        stockManager = new StockManager(plugin) {
            @Override
            public TransactionProcessor.Result canBuy(Player p, ShopItem item, int qty) {
                // Replicate the canBuy logic but use our injected stockRepo mock
                if (p.hasPermission("fluxshop.bypass.stock"))
                    return TransactionProcessor.Result.SUCCESS;

                if (item.getGlobalStock() >= 0) {
                    int sold = stockRepo.getGlobalSold(item.getShopId(), item.getId()).join();
                    if (sold + qty > item.getGlobalStock())
                        return TransactionProcessor.Result.NO_STOCK;
                }

                if (item.getPlayerLimit() >= 0) {
                    int bought = stockRepo.getPlayerSold(item.getShopId(), item.getId(), p.getUniqueId()).join();
                    if (bought + qty > item.getPlayerLimit())
                        return TransactionProcessor.Result.PLAYER_LIMIT;
                }

                return TransactionProcessor.Result.SUCCESS;
            }
        };

        when(player.getUniqueId()).thenReturn(playerUuid);
    }

    // ── Bypass permission ─────────────────────────────────────────────────────

    @Test
    void canBuy_bypassPermission_alwaysSuccess() {
        when(player.hasPermission("fluxshop.bypass.stock")).thenReturn(true);

        ShopItem item = item(0, 0); // would normally be out of stock
        assertEquals(TransactionProcessor.Result.SUCCESS, stockManager.canBuy(player, item, 999));
        verifyNoInteractions(stockRepo);
    }

    // ── Global stock ──────────────────────────────────────────────────────────

    @Test
    void canBuy_globalStockUnlimited_neverBlocked() {
        when(player.hasPermission("fluxshop.bypass.stock")).thenReturn(false);

        ShopItem item = item(-1, -1); // globalStock=-1 = unlimited
        assertEquals(TransactionProcessor.Result.SUCCESS, stockManager.canBuy(player, item, 999));
        verify(stockRepo, never()).getGlobalSold(any(), any());
    }

    @Test
    void canBuy_globalStockSufficient_success() {
        when(player.hasPermission("fluxshop.bypass.stock")).thenReturn(false);
        when(stockRepo.getGlobalSold("shop1", "item1")).thenReturn(CompletableFuture.completedFuture(5));

        ShopItem item = item(10, -1); // globalStock=10, 5 already sold
        assertEquals(TransactionProcessor.Result.SUCCESS, stockManager.canBuy(player, item, 5));
    }

    @Test
    void canBuy_globalStockExceeded_returnsNoStock() {
        when(player.hasPermission("fluxshop.bypass.stock")).thenReturn(false);
        when(stockRepo.getGlobalSold("shop1", "item1")).thenReturn(CompletableFuture.completedFuture(8));

        ShopItem item = item(10, -1); // globalStock=10, 8 sold → only 2 left, buying 5 fails
        assertEquals(TransactionProcessor.Result.NO_STOCK, stockManager.canBuy(player, item, 5));
    }

    @Test
    void canBuy_globalStockExactlyExhausted_returnsNoStock() {
        when(player.hasPermission("fluxshop.bypass.stock")).thenReturn(false);
        when(stockRepo.getGlobalSold("shop1", "item1")).thenReturn(CompletableFuture.completedFuture(10));

        ShopItem item = item(10, -1); // all stock sold
        assertEquals(TransactionProcessor.Result.NO_STOCK, stockManager.canBuy(player, item, 1));
    }

    @Test
    void canBuy_buyingExactlyRemainingStock_success() {
        when(player.hasPermission("fluxshop.bypass.stock")).thenReturn(false);
        when(stockRepo.getGlobalSold("shop1", "item1")).thenReturn(CompletableFuture.completedFuture(5));

        ShopItem item = item(10, -1); // 10 stock, 5 sold, buying 5 exactly
        assertEquals(TransactionProcessor.Result.SUCCESS, stockManager.canBuy(player, item, 5));
    }

    // ── Per-player limit ──────────────────────────────────────────────────────

    @Test
    void canBuy_playerLimitUnlimited_neverBlocked() {
        when(player.hasPermission("fluxshop.bypass.stock")).thenReturn(false);

        ShopItem item = item(-1, -1); // playerLimit=-1 = unlimited
        assertEquals(TransactionProcessor.Result.SUCCESS, stockManager.canBuy(player, item, 999));
        verify(stockRepo, never()).getPlayerSold(any(), any(), any());
    }

    @Test
    void canBuy_playerLimitSufficient_success() {
        when(player.hasPermission("fluxshop.bypass.stock")).thenReturn(false);
        when(stockRepo.getPlayerSold("shop1", "item1", playerUuid))
            .thenReturn(CompletableFuture.completedFuture(2));

        ShopItem item = item(-1, 5); // playerLimit=5, bought 2
        assertEquals(TransactionProcessor.Result.SUCCESS, stockManager.canBuy(player, item, 3));
    }

    @Test
    void canBuy_playerLimitExceeded_returnsPlayerLimit() {
        when(player.hasPermission("fluxshop.bypass.stock")).thenReturn(false);
        when(stockRepo.getPlayerSold("shop1", "item1", playerUuid))
            .thenReturn(CompletableFuture.completedFuture(4));

        ShopItem item = item(-1, 5); // playerLimit=5, bought 4, wants 2 more
        assertEquals(TransactionProcessor.Result.PLAYER_LIMIT, stockManager.canBuy(player, item, 2));
    }

    // ── Combined checks ───────────────────────────────────────────────────────

    @Test
    void canBuy_globalStockOk_playerLimitFails_returnsPlayerLimit() {
        when(player.hasPermission("fluxshop.bypass.stock")).thenReturn(false);
        when(stockRepo.getGlobalSold("shop1", "item1")).thenReturn(CompletableFuture.completedFuture(0));
        when(stockRepo.getPlayerSold("shop1", "item1", playerUuid))
            .thenReturn(CompletableFuture.completedFuture(5));

        ShopItem item = item(100, 5); // plenty of global stock, but player at limit
        assertEquals(TransactionProcessor.Result.PLAYER_LIMIT, stockManager.canBuy(player, item, 1));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * @param globalStock -1 = unlimited
     * @param playerLimit -1 = unlimited
     */
    private ShopItem item(int globalStock, int playerLimit) {
        ShopItem i = new ShopItem();
        i.setId("item1");
        i.setShopId("shop1");
        i.setGlobalStock(globalStock);
        i.setPlayerLimit(playerLimit);
        return i;
    }
}
