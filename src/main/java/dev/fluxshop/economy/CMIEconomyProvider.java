package dev.fluxshop.economy;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Economy provider backed by CMI (Zrips' plugin).
 *
 * <p>Uses reflection to avoid a hard compile-time dependency on CMI's API JAR.
 * CMI exposes economy through its own API class {@code com.Zrips.CMI.CMI}.
 */
public class CMIEconomyProvider implements EconomyProvider {

    private final FluxShopPlugin plugin;
    private Object               cmiEconomy;  // CMI economy object obtained via reflection

    public CMIEconomyProvider(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getId()                { return "cmi"; }
    @Override public String getDisplayName()       { return "CMI Economy"; }
    @Override public String getCurrencySymbol()    { return "$"; }
    @Override public String getCurrencyName()      { return "Dollar"; }
    @Override public String getCurrencyNamePlural(){ return "Dollars"; }

    @Override
    public boolean isAvailable() {
        if (Bukkit.getPluginManager().getPlugin("CMI") == null) return false;
        try {
            Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
            Object   instance = cmiClass.getMethod("getInstance").invoke(null);
            cmiEconomy = cmiClass.getMethod("getEconomyManager").invoke(instance);
            return cmiEconomy != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public double getBalance(UUID uuid) {
        try {
            Object user = getCMIUser(uuid);
            if (user == null) return 0.0;
            Method getBalance = user.getClass().getMethod("getBalance");
            return (double) getBalance.invoke(user);
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
            Object user = getCMIUser(uuid);
            if (user == null) return false;
            Method deposit = user.getClass().getMethod("deposit", double.class);
            deposit.invoke(user, amount);
            saveUser(user);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        try {
            if (!has(uuid, amount)) return false;
            Object user = getCMIUser(uuid);
            if (user == null) return false;
            Method withdraw = user.getClass().getMethod("withdraw", double.class);
            withdraw.invoke(user, amount);
            saveUser(user);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return String.format("$%.2f", amount);
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private Object getCMIUser(UUID uuid) throws Exception {
        Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
        Object   instance = cmiClass.getMethod("getInstance").invoke(null);
        Object   users    = cmiClass.getMethod("getPlayerManager").invoke(instance);
        return users.getClass().getMethod("getUser", UUID.class).invoke(users, uuid);
    }

    private void saveUser(Object user) {
        try {
            user.getClass().getMethod("save").invoke(user);
        } catch (Exception ignored) {}
    }
}
