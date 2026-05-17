package dev.fluxshop.economy;

import dev.flux.api.FluxAPI;
import dev.flux.api.economy.EconomyService;
import dev.fluxshop.FluxShopPlugin;

import java.util.UUID;

/**
 * Economy provider backed by the Flux plugin's {@link EconomyService}.
 *
 * <p>This is the highest-priority provider when Flux is installed on the server.
 * All operations are asynchronous inside Flux but we block here briefly since
 * FluxShop's transaction flow is already handled off the main thread.
 */
public class FluxEconomyProvider implements EconomyProvider {

    private final FluxShopPlugin plugin;
    private EconomyService       economy;

    public FluxEconomyProvider(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId()             { return "flux"; }
    @Override
    public String getDisplayName()    { return "Flux Economy"; }
    @Override
    public String getCurrencySymbol() {
        return economy != null ? economy.getCurrencySymbol() : "$";
    }
    @Override
    public String getCurrencyName() {
        return economy != null ? economy.getCurrencyName() : "Dollar";
    }
    @Override
    public String getCurrencyNamePlural() {
        return economy != null ? economy.getCurrencyNamePlural() : "Dollars";
    }

    @Override
    public boolean isAvailable() {
        if (!FluxAPI.isAvailable()) return false;
        try {
            economy = FluxAPI.getEconomy();
            return economy != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public double getBalance(UUID uuid) {
        try {
            return economy.getBalance(uuid).join();
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        try {
            return economy.has(uuid, amount).join();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        try {
            economy.deposit(uuid, amount).join();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[FluxEconomy] deposit failed for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        try {
            economy.withdraw(uuid, amount).join();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[FluxEconomy] withdraw failed for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public String format(double amount) {
        try {
            return economy.format(amount);
        } catch (Exception e) {
            return String.format("%.2f", amount);
        }
    }
}
