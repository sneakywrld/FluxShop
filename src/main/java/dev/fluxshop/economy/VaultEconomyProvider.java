package dev.fluxshop.economy;

import dev.fluxshop.FluxShopPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

/**
 * Economy provider backed by Vault.
 *
 * <p>Supports any Vault-compatible economy plugin: EssentialsX Economy,
 * iConomy, CMI, TheNewEconomy, etc.
 */
public class VaultEconomyProvider implements EconomyProvider {

    private final FluxShopPlugin plugin;
    private Economy              vault;

    public VaultEconomyProvider(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId()             { return "vault"; }
    @Override
    public String getDisplayName()    { return vault != null ? "Vault (" + vault.getName() + ")" : "Vault"; }
    @Override
    public String getCurrencySymbol() { return ""; }  // Vault doesn't expose symbol
    @Override
    public String getCurrencyName()   { return vault != null ? vault.currencyNameSingular() : "Dollar"; }
    @Override
    public String getCurrencyNamePlural() { return vault != null ? vault.currencyNamePlural() : "Dollars"; }

    @Override
    public boolean isAvailable() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
            Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        vault = rsp.getProvider();
        return vault != null;
    }

    @Override
    public double getBalance(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return vault.getBalance(op);
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        return vault.has(Bukkit.getOfflinePlayer(uuid), amount);
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        return vault.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount).transactionSuccess();
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        return vault.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount).transactionSuccess();
    }

    @Override
    public String format(double amount) {
        return vault.format(amount);
    }
}
