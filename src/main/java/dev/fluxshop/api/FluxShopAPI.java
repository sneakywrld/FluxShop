package dev.fluxshop.api;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.auction.AuctionService;
import dev.fluxshop.blackmarket.BlackMarketService;
import dev.fluxshop.economy.EconomyRegistry;
import dev.fluxshop.shop.ShopManager;
import dev.fluxshop.storage.AnalyticsRepository;
import dev.fluxshop.trade.TradeManager;

/**
 * Public static API facade for FluxShop.
 *
 * <p>Available after the plugin's {@code onEnable()} completes.
 * External plugins should soft-depend on FluxShop and check
 * {@link #isAvailable()} before using this API.
 *
 * <pre>{@code
 * if (FluxShopAPI.isAvailable()) {
 *     ShopManager shops = FluxShopAPI.getShopManager();
 *     // ...
 * }
 * }</pre>
 */
public final class FluxShopAPI {

    private static FluxShopPlugin instance;

    private FluxShopAPI() {}

    /**
     * Called internally by FluxShopPlugin during onEnable.
     */
    public static void init(FluxShopPlugin plugin) {
        instance = plugin;
    }

    /**
     * Called internally during onDisable.
     */
    public static void shutdown() {
        instance = null;
    }

    /**
     * Returns true if FluxShop has fully loaded and the API is ready.
     */
    public static boolean isAvailable() {
        return instance != null;
    }

    /**
     * Access the shop registry — load shops, look up items, etc.
     *
     * @throws IllegalStateException if FluxShop is not loaded
     */
    public static ShopManager getShopManager() {
        assertAvailable();
        return instance.getShopManager();
    }

    /**
     * Access the economy registry — get providers, check balances, etc.
     */
    public static EconomyRegistry getEconomyRegistry() {
        assertAvailable();
        return instance.getEconomyRegistry();
    }

    public static AuctionService getAuctionService() {
        assertAvailable();
        return instance.getAuctionService();
    }

    public static TradeManager getTradeManager() {
        assertAvailable();
        return instance.getTradeManager();
    }

    public static BlackMarketService getBlackMarketService() {
        assertAvailable();
        return instance.getBlackMarketService();
    }

    public static AnalyticsRepository getAnalytics() {
        assertAvailable();
        return instance.getAnalyticsRepository();
    }

    /**
     * Access the raw plugin instance (advanced use only).
     */
    public static FluxShopPlugin getPlugin() {
        assertAvailable();
        return instance;
    }

    private static void assertAvailable() {
        if (instance == null) {
            throw new IllegalStateException("FluxShopAPI is not available — is FluxShop loaded?");
        }
    }
}
