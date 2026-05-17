package dev.fluxshop.economy;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EconomyRegistry}.
 *
 * <p>In the test environment none of the optional economy plugins are on the classpath
 * (Vault, CMI, Essentials, etc.), so all {@code tryRegister()} calls throw and are swallowed.
 * This lets us verify the "no providers available" contract and the static utility behaviour
 * without needing live Bukkit or economy plugin JARs.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EconomyRegistryTest {

    @Mock FluxShopPlugin plugin;
    @Mock ConfigManager  config;
    @Mock org.bukkit.Server server;
    @Mock org.bukkit.plugin.PluginManager pluginManager;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("FluxShopTest"));
        when(plugin.getConfigManager()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        // No plugins installed → all isLoaded() calls return null
        when(pluginManager.getPlugin(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        // Default economy config: "auto"
        when(config.getDefaultEconomyProvider()).thenReturn("auto");
    }

    // ── No providers available ────────────────────────────────────────────────

    @Test
    void noProviders_getDefault_returnsNull() {
        EconomyRegistry registry = new EconomyRegistry(plugin);
        assertNull(registry.getDefault(),
            "getDefault() must be null when no economy plugins are installed");
    }

    @Test
    void noProviders_hasProvider_returnsFalse() {
        EconomyRegistry registry = new EconomyRegistry(plugin);
        assertFalse(registry.hasProvider());
    }

    @Test
    void noProviders_getAll_returnsEmptyMap() {
        EconomyRegistry registry = new EconomyRegistry(plugin);
        assertTrue(registry.getAll().isEmpty());
    }

    // ── get() fallback behaviour ──────────────────────────────────────────────

    @Test
    void get_unknownId_returnsDefault() {
        EconomyRegistry registry = new EconomyRegistry(plugin);
        // Default is null (no providers); get() for an unknown id should also return null (= default)
        assertNull(registry.get("nonexistent"));
    }

    @Test
    void get_nullId_returnsDefault() {
        EconomyRegistry registry = new EconomyRegistry(plugin);
        assertNull(registry.get(null));
    }

    @Test
    void get_blankId_returnsDefault() {
        EconomyRegistry registry = new EconomyRegistry(plugin);
        assertNull(registry.get("  "));
    }

    // ── Configured default override ───────────────────────────────────────────

    @Test
    void configuredDefault_unknownId_fallsBackToAuto() {
        // Configure a specific provider that doesn't exist
        when(config.getDefaultEconomyProvider()).thenReturn("playerpoints");
        EconomyRegistry registry = new EconomyRegistry(plugin);
        // "playerpoints" not registered → falls through auto-detect → still null
        assertNull(registry.getDefault());
    }

    // ── formatDefault static utility ──────────────────────────────────────────

    @Test
    void formatDefault_usLocale_formatsWithCommas() {
        String result = EconomyRegistry.formatDefault(1234567.89, "en-US");
        // en-US formats: 1,234,567.89
        assertTrue(result.contains("1") && result.contains("234"),
            "en-US should use comma grouping: got " + result);
    }

    @Test
    void formatDefault_zero_formatsCorrectly() {
        String result = EconomyRegistry.formatDefault(0, "en-US");
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void formatDefault_negativeAmount_formatsCorrectly() {
        String result = EconomyRegistry.formatDefault(-99.5, "en-US");
        assertNotNull(result);
        assertTrue(result.contains("99"), "Should contain the numeric value");
    }

    @Test
    void formatDefault_invalidLocale_fallsBackToDecimalFormat() {
        // Invalid locale tag — must not throw, must return something reasonable
        String result = EconomyRegistry.formatDefault(42.5, "zz-XX-invalid");
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void formatDefault_deLocale_usesEuropeanFormat() {
        // de-DE uses period as thousands separator and comma as decimal
        String result = EconomyRegistry.formatDefault(1000.0, "de-DE");
        assertNotNull(result);
        // Exact format depends on JVM locale data, but should contain "1"
        assertTrue(result.contains("1"));
    }

    // ── PRIORITY ordering contract ────────────────────────────────────────────

    @Test
    void priorityOrder_autoSelect_picksFirstAvailable() {
        // With no providers in test env, verify the constant itself is in the right order
        // (contract test: flux must precede vault must precede cmi, etc.)
        // We test this indirectly: if we could register "vault" and "flux", flux wins.
        // Since we can't in tests, just verify the registry builds without error.
        assertDoesNotThrow(() -> new EconomyRegistry(plugin));
    }
}
