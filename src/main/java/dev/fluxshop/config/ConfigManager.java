package dev.fluxshop.config;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages all FluxShop configuration files.
 *
 * <p>Handles loading, reloading, and safe access with typed getters.
 * Every config file is versioned; on load the file is migrated forward
 * if new keys have been added in a plugin update.
 */
public class ConfigManager {

    private static final int CURRENT_CONFIG_VERSION = 1;

    private final FluxShopPlugin plugin;

    /** Main config.yml */
    private FileConfiguration config;

    /** messages.yml */
    private FileConfiguration messages;

    /** GUI config files keyed by their name (e.g. "main_menu", "category") */
    private final Map<String, FileConfiguration> guiConfigs = new HashMap<>();

    private static final String[] GUI_FILES = {
        "main_menu", "category", "buy_screen", "sell_screen",
        "sellgui", "auction_house", "trade", "black_market", "analytics"
    };

    public ConfigManager(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Loads (or reloads) all configuration files from disk.
     * Saves defaults on first run. Migrates old configs forward.
     */
    public void load() {
        saveDefaultIfMissing("config.yml");
        saveDefaultIfMissing("messages.yml");
        for (String gui : GUI_FILES) {
            saveDefaultIfMissing("guis/" + gui + ".yml");
        }

        config   = loadYaml("config.yml");
        messages = loadYaml("messages.yml");

        guiConfigs.clear();
        for (String gui : GUI_FILES) {
            guiConfigs.put(gui, loadYaml("guis/" + gui + ".yml"));
        }

        migrate(config, "config.yml");
        plugin.getLogger().info("Configuration loaded.");
    }

    // ── Config accessors ──────────────────────────────────────────────────────

    public FileConfiguration getConfig()   { return config;   }
    public FileConfiguration getMessages() { return messages; }

    public FileConfiguration getGuiConfig(String name) {
        return guiConfigs.getOrDefault(name, config);
    }

    // ── Typed convenience getters ─────────────────────────────────────────────

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    public boolean isDebug() {
        return getBoolean("general.debug", false);
    }

    public String getStorageType() {
        return getString("database.storage-type", "SQLITE").toUpperCase();
    }

    public String getDefaultEconomyProvider() {
        return getString("economy.default-provider", "AUTO").toUpperCase();
    }

    public boolean isSoundsEnabled(String event) {
        return getBoolean("sounds." + event + ".enabled", true);
    }

    public String getSound(String event) {
        return getString("sounds." + event + ".sound", "UI_BUTTON_CLICK");
    }

    public float getSoundVolume(String event) {
        return (float) getDouble("sounds." + event + ".volume", 1.0);
    }

    public float getSoundPitch(String event) {
        return (float) getDouble("sounds." + event + ".pitch", 1.0);
    }

    public boolean isParticleEnabled(String event) {
        return getBoolean("particles." + event + ".enabled", true);
    }

    public String getParticleType(String event) {
        return getString("particles." + event + ".type", "VILLAGER_HAPPY");
    }

    public int getParticleCount(String event) {
        return getInt("particles." + event + ".count", 10);
    }

    public double getParticleOffsetX(String event) { return getDouble("particles." + event + ".offset-x", 0.5); }
    public double getParticleOffsetY(String event) { return getDouble("particles." + event + ".offset-y", 0.5); }
    public double getParticleOffsetZ(String event) { return getDouble("particles." + event + ".offset-z", 0.5); }
    public double getParticleSpeed(String event)   { return getDouble("particles." + event + ".speed", 0.1); }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Copies a resource from the JAR to the plugin data folder if it does not exist.
     */
    private void saveDefaultIfMissing(String resourcePath) {
        File dest = new File(plugin.getDataFolder(), resourcePath);
        if (!dest.exists()) {
            // Ensure parent directories exist
            //noinspection ResultOfMethodCallIgnored
            dest.getParentFile().mkdirs();
            try {
                plugin.saveResource(resourcePath, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Config] Default resource not found in JAR: " + resourcePath);
            }
        }
    }

    /**
     * Loads a YAML file from the plugin data folder and applies any defaults
     * from the bundled resource.
     */
    private FileConfiguration loadYaml(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Apply defaults from the bundled resource so new keys auto-populate
        InputStream defaultStream = plugin.getResource(resourcePath);
        if (defaultStream != null) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            cfg.setDefaults(defaults);
        }
        return cfg;
    }

    /**
     * Compares the file's config-version against {@link #CURRENT_CONFIG_VERSION}.
     * If outdated, adds missing keys (without overwriting existing ones) and saves.
     */
    private void migrate(FileConfiguration cfg, String name) {
        int fileVersion = cfg.getInt("config-version", 0);
        if (fileVersion >= CURRENT_CONFIG_VERSION) return;

        plugin.getLogger().info("Migrating " + name + " from version " + fileVersion + " to " + CURRENT_CONFIG_VERSION);

        // copyDefaults adds any missing keys from defaults (set above in loadYaml)
        cfg.options().copyDefaults(true);
        cfg.set("config-version", CURRENT_CONFIG_VERSION);

        File file = new File(plugin.getDataFolder(), name);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save migrated " + name, e);
        }
    }

    /**
     * Saves a FileConfiguration to disk.
     */
    public void save(FileConfiguration cfg, String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + resourcePath, e);
        }
    }
}
