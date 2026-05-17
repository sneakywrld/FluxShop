package dev.fluxshop.economy;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Abstraction over any economy plugin.
 *
 * <p>Implementations: {@link FluxEconomyProvider}, {@link VaultEconomyProvider},
 * {@link CMIEconomyProvider}, {@link EssentialsEconomyProvider},
 * {@link PlayerPointsProvider}, {@link CoinsEngineProvider}.
 */
public interface EconomyProvider {

    /** Unique identifier for this provider (e.g. "flux", "vault", "cmi"). */
    String getId();

    /** Human-readable name shown in GUIs and logs. */
    String getDisplayName();

    /** Currency symbol (e.g. "$", "⛂"). */
    String getCurrencySymbol();

    /** Singular currency name (e.g. "Dollar"). */
    String getCurrencyName();

    /** Plural currency name (e.g. "Dollars"). */
    String getCurrencyNamePlural();

    /** Returns the player's current balance. */
    double getBalance(UUID uuid);

    /** Returns {@code true} if the player has at least {@code amount}. */
    boolean has(UUID uuid, double amount);

    /**
     * Deposits {@code amount} into the player's account.
     *
     * @return {@code true} on success
     */
    boolean deposit(UUID uuid, double amount);

    /**
     * Withdraws {@code amount} from the player's account.
     *
     * @return {@code true} on success; {@code false} if insufficient funds
     */
    boolean withdraw(UUID uuid, double amount);

    /**
     * Formats {@code amount} according to this economy's locale/format rules.
     * E.g. 1000.0 → "$1,000.00"
     */
    String format(double amount);

    /**
     * Returns {@code true} if this provider is currently available
     * (i.e. the backing plugin is loaded and ready).
     */
    boolean isAvailable();
}
