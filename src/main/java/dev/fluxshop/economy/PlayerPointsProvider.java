package dev.fluxshop.economy;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Economy provider backed by PlayerPoints.
 *
 * <p>PlayerPoints uses integer (point) balances. Doubles are cast to int.
 * All API access uses reflection to avoid compile-time dependency on the plugin JAR.
 */
public class PlayerPointsProvider implements EconomyProvider {

    private final FluxShopPlugin plugin;
    private Object               api;   // PlayerPointsAPI instance

    public PlayerPointsProvider(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getId()                { return "playerpoints"; }
    @Override public String getDisplayName()       { return "PlayerPoints"; }
    @Override public String getCurrencySymbol()    { return "⛂"; }
    @Override public String getCurrencyName()      { return "Point"; }
    @Override public String getCurrencyNamePlural(){ return "Points"; }

    @Override
    public boolean isAvailable() {
        try {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (p == null || !p.isEnabled()) return false;
            Method getAPI = p.getClass().getMethod("getAPI");
            api = getAPI.invoke(p);
            return api != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public double getBalance(UUID uuid) {
        try {
            Method look = api.getClass().getMethod("look", UUID.class);
            Object result = look.invoke(api, uuid);
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
            Method give = api.getClass().getMethod("give", UUID.class, int.class);
            Object result = give.invoke(api, uuid, (int) amount);
            return result instanceof Boolean b && b;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        try {
            Method take = api.getClass().getMethod("take", UUID.class, int.class);
            Object result = take.invoke(api, uuid, (int) amount);
            return result instanceof Boolean b && b;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return (int) amount + " Points";
    }
}
