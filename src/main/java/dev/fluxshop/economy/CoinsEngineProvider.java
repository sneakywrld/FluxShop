package dev.fluxshop.economy;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Economy provider backed by CoinsEngine.
 *
 * <p>Uses reflection to avoid a compile-time dependency.
 */
public class CoinsEngineProvider implements EconomyProvider {

    private final FluxShopPlugin plugin;
    private Object               coinsAPI; // su.nightexpress.coinsengine.api.CoinsEngineAPI

    public CoinsEngineProvider(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getId()                { return "coinsengine"; }
    @Override public String getDisplayName()       { return "CoinsEngine"; }
    @Override public String getCurrencySymbol()    { return "¢"; }
    @Override public String getCurrencyName()      { return "Coin"; }
    @Override public String getCurrencyNamePlural(){ return "Coins"; }

    @Override
    public boolean isAvailable() {
        if (Bukkit.getPluginManager().getPlugin("CoinsEngine") == null) return false;
        try {
            Class<?> apiClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            coinsAPI = apiClass;
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public double getBalance(UUID uuid) {
        try {
            // CoinsEngineAPI.getBalance(uuid, currency) — use default currency
            Method m = ((Class<?>) coinsAPI).getMethod("getBalance", UUID.class, String.class);
            Object result = m.invoke(null, uuid, "coins");
            return result instanceof Number n ? n.doubleValue() : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        try {
            Method m = ((Class<?>) coinsAPI).getMethod("addBalance", UUID.class, String.class, double.class);
            m.invoke(null, uuid, "coins", amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        try {
            if (!has(uuid, amount)) return false;
            Method m = ((Class<?>) coinsAPI).getMethod("removeBalance", UUID.class, String.class, double.class);
            m.invoke(null, uuid, "coins", amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return String.format("%.0f Coins", amount);
    }
}
