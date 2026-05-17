package dev.fluxshop.shop;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.ShopStand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Manages physical shop-stand blocks — regular blocks in the world that
 * open a shop GUI when right-clicked.
 *
 * <p>Mappings are persisted to {@code stands.yml} in the plugin's data folder
 * so they survive server restarts. Admins register stands in-game via
 * {@code /fluxshop stand set <shopId>} (targeting the block they look at).
 *
 * <h3>YAML format</h3>
 * <pre>
 * stands:
 *   "world,10,64,-5": "survival"
 *   "world,10,64,-6": "tools"
 * </pre>
 */
public class ShopStandManager {

    private static final String KEY_PREFIX = "stands.";

    private final FluxShopPlugin        plugin;
    private final File                  file;
    private       YamlConfiguration     cfg;

    /** In-memory lookup: serialised location key → shop id */
    private final Map<String, String> stands = new HashMap<>();

    public ShopStandManager(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "stands.yml");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void load() {
        if (!file.exists()) {
            cfg = new YamlConfiguration();
            return;
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        stands.clear();

        var section = cfg.getConfigurationSection("stands");
        if (section != null) {
            for (String locKey : section.getKeys(false)) {
                String shopId = section.getString(locKey);
                if (shopId != null && !shopId.isBlank()) {
                    stands.put(locKey, shopId);
                }
            }
        }
        plugin.getLogger().info("[ShopStandManager] Loaded " + stands.size() + " shop stand(s).");
    }

    public void save() {
        if (cfg == null) cfg = new YamlConfiguration();
        cfg.set("stands", null); // clear existing before rewriting
        stands.forEach((loc, id) -> cfg.set(KEY_PREFIX + loc, id));
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[ShopStandManager] Failed to save stands.yml", e);
        }
    }

    // ── Stand registration ────────────────────────────────────────────────────

    /**
     * Registers a block as a shop stand for the given shop id.
     * Overwrites any existing registration for that block.
     */
    public void addStand(Location location, String shopId) {
        stands.put(serialize(location), shopId);
        save();
    }

    /**
     * Removes a shop stand registration from the given block.
     *
     * @return true if a stand was registered there, false if nothing was removed
     */
    public boolean removeStand(Location location) {
        String removed = stands.remove(serialize(location));
        if (removed != null) save();
        return removed != null;
    }

    /**
     * Returns the shop id for a block, or {@code null} if the block is not a stand.
     */
    public String getShopId(Location location) {
        return stands.get(serialize(location));
    }

    /** Returns {@code true} if the given block is a registered shop stand. */
    public boolean isStand(Location location) {
        return stands.containsKey(serialize(location));
    }

    /** Returns all registered stands as typed {@link ShopStand} objects. */
    public List<ShopStand> getAllStands() {
        List<ShopStand> result = new ArrayList<>(stands.size());
        for (Map.Entry<String, String> entry : stands.entrySet()) {
            ShopStand stand = ShopStand.fromEntry(entry.getKey(), entry.getValue());
            if (stand != null) result.add(stand);
        }
        return Collections.unmodifiableList(result);
    }

    /** Returns an unmodifiable view of all registered stands (locKey → shopId). */
    public Set<Map.Entry<String, String>> allStands() {
        return Collections.unmodifiableSet(stands.entrySet());
    }

    /** Total number of registered stands. */
    public int size() {
        return stands.size();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Serialises a Location to a stable string key:
     * {@code "<worldName>,<blockX>,<blockY>,<blockZ>"}.
     */
    public static String serialize(Location loc) {
        return loc.getWorld().getName() + ","
            + loc.getBlockX() + ","
            + loc.getBlockY() + ","
            + loc.getBlockZ();
    }

    /**
     * Deserialises a key back to a Location. Returns {@code null} if the world
     * is not loaded or the key is malformed.
     */
    public static Location deserialize(String key) {
        if (key == null) return null;
        String[] parts = key.split(",", 4);
        if (parts.length != 4) return null;
        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
