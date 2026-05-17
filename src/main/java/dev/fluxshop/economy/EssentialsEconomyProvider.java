package dev.fluxshop.economy;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Economy provider backed by EssentialsX.
 *
 * <p>Uses the EssentialsX API via reflection to avoid compile-time dependency
 * on the EssentialsX plugin JAR which is not available from standard Maven repos.
 */
public class EssentialsEconomyProvider implements EconomyProvider {

    private final FluxShopPlugin plugin;
    private Object               ess;     // IEssentials instance (via reflection)

    public EssentialsEconomyProvider(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getId()                { return "essentials"; }
    @Override public String getDisplayName()       { return "EssentialsX Economy"; }
    @Override public String getCurrencySymbol()    { return "$"; }
    @Override public String getCurrencyName()      { return "Dollar"; }
    @Override public String getCurrencyNamePlural(){ return "Dollars"; }

    @Override
    public boolean isAvailable() {
        try {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("Essentials");
            if (p == null || !p.isEnabled()) return false;
            // Verify it implements IEssentials
            Class<?> iEssentials = Class.forName("net.ess3.api.IEssentials");
            if (!iEssentials.isInstance(p)) return false;
            ess = p;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public double getBalance(UUID uuid) {
        try {
            Object user = getUser(uuid);
            if (user == null) return 0.0;
            Method getMoney = user.getClass().getMethod("getMoney");
            Object bal = getMoney.invoke(user);
            return bal instanceof BigDecimal bd ? bd.doubleValue() : 0.0;
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
            Object user = getUser(uuid);
            if (user == null) return false;
            Method getMoney  = user.getClass().getMethod("getMoney");
            Method setMoney  = user.getClass().getMethod("setMoney", BigDecimal.class, Class.forName("com.earth2me.essentials.api.UserBalanceUpdateEvent$Cause"));
            // Fallback to older API signature
            try {
                BigDecimal current = (BigDecimal) getMoney.invoke(user);
                setMoney.invoke(user, current.add(BigDecimal.valueOf(amount)), null);
            } catch (Exception ignored) {
                Method setMoney2 = user.getClass().getMethod("setMoney", BigDecimal.class);
                BigDecimal current = (BigDecimal) getMoney.invoke(user);
                setMoney2.invoke(user, current.add(BigDecimal.valueOf(amount)));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        try {
            if (!has(uuid, amount)) return false;
            Object user = getUser(uuid);
            if (user == null) return false;
            Method getMoney = user.getClass().getMethod("getMoney");
            BigDecimal current = (BigDecimal) getMoney.invoke(user);
            try {
                Method setMoney = user.getClass().getMethod("setMoney", BigDecimal.class, Class.forName("com.earth2me.essentials.api.UserBalanceUpdateEvent$Cause"));
                setMoney.invoke(user, current.subtract(BigDecimal.valueOf(amount)), null);
            } catch (Exception ignored) {
                Method setMoney2 = user.getClass().getMethod("setMoney", BigDecimal.class);
                setMoney2.invoke(user, current.subtract(BigDecimal.valueOf(amount)));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return String.format("$%.2f", amount);
    }

    private Object getUser(UUID uuid) {
        try {
            Method getUser = ess.getClass().getMethod("getUser", UUID.class);
            return getUser.invoke(ess, uuid);
        } catch (Exception e) {
            return null;
        }
    }
}
