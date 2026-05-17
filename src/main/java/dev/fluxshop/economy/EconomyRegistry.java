package dev.fluxshop.economy;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.plugin.Plugin;

import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Detects, registers, and manages all economy providers.
 *
 * <p>On startup it probes each provider in priority order and selects the first available one
 * as the default. Per-shop and per-item overrides can request a specific provider by ID.
 */
public class EconomyRegistry {

    /** Detection priority order */
    private static final String[] PRIORITY = { "flux", "vault", "cmi", "essentials", "playerpoints", "coinsengine" };

    private final FluxShopPlugin                plugin;
    private final Map<String, EconomyProvider>  providers = new LinkedHashMap<>();
    private EconomyProvider                     defaultProvider;

    public EconomyRegistry(FluxShopPlugin plugin) {
        this.plugin = plugin;
        registerAll();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    private void registerAll() {
        Logger log = plugin.getLogger();

        tryRegister("flux",         () -> new FluxEconomyProvider(plugin));
        tryRegister("vault",        () -> new VaultEconomyProvider(plugin));
        tryRegister("cmi",          () -> new CMIEconomyProvider(plugin));
        tryRegister("essentials",   () -> new EssentialsEconomyProvider(plugin));
        tryRegister("playerpoints", () -> new PlayerPointsProvider(plugin));
        tryRegister("coinsengine",  () -> new CoinsEngineProvider(plugin));

        // Determine default
        String configured = plugin.getConfigManager().getDefaultEconomyProvider().toLowerCase();
        if (!configured.equals("auto") && providers.containsKey(configured)) {
            defaultProvider = providers.get(configured);
        } else {
            // Auto: pick first available in priority order
            for (String id : PRIORITY) {
                if (providers.containsKey(id)) {
                    defaultProvider = providers.get(id);
                    break;
                }
            }
        }

        if (defaultProvider == null) {
            log.severe("No economy provider found! Please install Vault, Flux, CMI, or Essentials.");
        } else {
            log.info("Economy provider: " + defaultProvider.getDisplayName());
        }
    }

    private void tryRegister(String id, java.util.function.Supplier<EconomyProvider> factory) {
        try {
            EconomyProvider ep = factory.get();
            if (ep.isAvailable()) {
                providers.put(id, ep);
                plugin.getLogger().info("Economy hook registered: " + ep.getDisplayName());
            }
        } catch (Throwable ignored) {
            // Plugin not installed or incompatible — skip silently
            // Must catch Throwable (not just Exception) because missing soft-depend
            // classes throw NoClassDefFoundError, which is an Error, not an Exception.
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the default economy provider. Never {@code null} after successful init. */
    public EconomyProvider getDefault() {
        return defaultProvider;
    }

    /**
     * Returns the provider with the given ID, or the default if not found.
     *
     * @param id provider identifier (e.g. "vault", "playerpoints")
     */
    public EconomyProvider get(String id) {
        if (id == null || id.isBlank()) return defaultProvider;
        return providers.getOrDefault(id.toLowerCase(), defaultProvider);
    }

    /** Returns {@code true} if at least one economy provider is available. */
    public boolean hasProvider() {
        return defaultProvider != null;
    }

    /** Returns all registered providers. */
    public Map<String, EconomyProvider> getAll() {
        return Map.copyOf(providers);
    }

    // ── Static format utility ─────────────────────────────────────────────────

    /**
     * Formats a number using the server locale configured in config.yml.
     * Used when a provider doesn't supply its own formatting.
     */
    public static String formatDefault(double amount, String localeTag) {
        try {
            Locale locale = Locale.forLanguageTag(localeTag);
            return NumberFormat.getNumberInstance(locale).format(amount);
        } catch (Exception e) {
            return String.format("%.2f", amount);
        }
    }
}
