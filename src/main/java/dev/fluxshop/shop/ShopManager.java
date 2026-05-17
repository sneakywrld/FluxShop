package dev.fluxshop.shop;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.Shop;
import dev.fluxshop.model.ShopCategory;
import dev.fluxshop.model.ShopItem;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;

/**
 * Central registry for all loaded {@link Shop}s.
 *
 * <p>Shops are loaded from YAML files in {@code <dataFolder>/shops/}.
 * The default survival shops are copied from the JAR on first startup.
 */
public class ShopManager {

    private final FluxShopPlugin             plugin;
    private final Map<String, Shop>          shops     = new ConcurrentHashMap<>();
    private final Map<String, File>          shopFiles = new ConcurrentHashMap<>();
    private final ShopLoader                 loader;

    public ShopManager(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.loader = new ShopLoader(plugin);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Loads all shops from the shops/ directory. */
    public void loadAll() {
        shops.clear();
        shopFiles.clear();
        File shopsDir = new File(plugin.getDataFolder(), "shops");
        if (!shopsDir.exists()) {
            shopsDir.mkdirs();
            copyDefaultShops();
        }

        File[] files = shopsDir.listFiles(f -> f.getName().endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("No shop files found in shops/. Copying defaults...");
            copyDefaultShops();
            files = shopsDir.listFiles(f -> f.getName().endsWith(".yml"));
        }

        if (files != null) {
            for (File file : files) {
                try {
                    loader.load(file).ifPresent(shop -> {
                        if (shop.isEnabled()) {
                            shops.put(shop.getId(), shop);
                            shopFiles.put(shop.getId(), file);
                            plugin.getLogger().info("  Loaded shop: " + shop.getId()
                                + " (" + shop.getCategories().size() + " categories)");
                        }
                    });
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load shop: " + file.getName(), e);
                }
            }
        }
        plugin.getLogger().info("Loaded " + shops.size() + " shops.");
    }

    /**
     * Updates a shop item's buy or sell price in memory and persists it to the source YAML file.
     *
     * @param shopId  the shop id
     * @param itemId  the item id (searched across all categories)
     * @param type    {@code "buy"} or {@code "sell"}
     * @param price   the new price
     * @return {@code true} if the item was found and updated; {@code false} if not found
     */
    public boolean updateItemPrice(String shopId, String itemId, String type, double price) {
        Shop shop = shops.get(shopId);
        if (shop == null) return false;

        for (ShopCategory cat : shop.getCategories()) {
            for (ShopItem item : cat.getItems()) {
                if (!item.getId().equals(itemId)) continue;

                // Update in-memory model
                if ("buy".equalsIgnoreCase(type)) item.setBuyPrice(price);
                else                              item.setSellPrice(price);

                // Persist to YAML
                File file = shopFiles.get(shopId);
                if (file != null && file.exists()) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    String priceKey = "buy".equalsIgnoreCase(type) ? "buy-price" : "sell-price";
                    String path = "categories." + cat.getId() + ".items." + itemId + "." + priceKey;
                    yaml.set(path, price);
                    try {
                        yaml.save(file);
                    } catch (IOException e) {
                        plugin.getLogger().warning("Failed to save price update to " + file.getName() + ": " + e.getMessage());
                    }
                }
                return true;
            }
        }
        return false;
    }

    /** Saves all shops to disk (currently a no-op — shops are config-defined). */
    public void saveAll() {
        // Shop data lives in YAML; changes via updateItemPrice() are written immediately.
    }

    // ── Lookups ───────────────────────────────────────────────────────────────

    public Optional<Shop> getShop(String id) {
        return Optional.ofNullable(shops.get(id));
    }

    /** Returns the raw shop or null. Use getShop(id) for Optional variant. */
    public Shop getShopOrNull(String id) {
        return shops.get(id);
    }

    public Shop getDefaultShop() {
        String defaultId = plugin.getConfigManager().getString("shop.default-shop", "main");
        return shops.getOrDefault(defaultId, shops.values().stream().findFirst().orElse(null));
    }

    public Collection<Shop> getAllShops() {
        return Collections.unmodifiableCollection(shops.values());
    }

    public int getShopCount() {
        return shops.size();
    }

    /**
     * Finds a {@link ShopItem} by its full path {@code shopId/categoryId/itemId}.
     */
    public Optional<ShopItem> findItem(String shopId, String categoryId, String itemId) {
        Shop shop = shops.get(shopId);
        if (shop == null) return Optional.empty();
        // "*" means search all categories
        if ("*".equals(categoryId)) {
            return shop.getCategories().stream()
                .flatMap(c -> c.getItems().stream())
                .filter(i -> i.getId().equals(itemId))
                .findFirst();
        }
        ShopCategory cat = shop.getCategory(categoryId);
        if (cat == null) return Optional.empty();
        return cat.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst();
    }

    /**
     * Searches all shops for items whose display item matches the given ItemStack material.
     * Used by /sellall to find the sell price for an item.
     */
    public List<ShopItem> findSellableItems(org.bukkit.inventory.ItemStack stack) {
        List<ShopItem> results = new ArrayList<>();
        if (stack == null || stack.getType().isAir()) return results;
        for (Shop shop : shops.values()) {
            for (ShopCategory cat : shop.getCategories()) {
                for (ShopItem item : cat.getItems()) {
                    if (item.isSellable() && matches(item, stack)) {
                        results.add(item);
                    }
                }
            }
        }
        return results;
    }

    /** Returns all sellable items across every shop (no material filter). */
    public List<ShopItem> findAllSellableItems() {
        List<ShopItem> results = new ArrayList<>();
        for (Shop shop : shops.values()) {
            for (ShopCategory cat : shop.getCategories()) {
                for (ShopItem item : cat.getItems()) {
                    if (item.isSellable()) results.add(item);
                }
            }
        }
        return results;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns true if a player's inventory item matches a shop item for sell purposes.
     * Material equality is always required; for potions and enchanted books, metadata
     * (potion type / stored enchantments) must also match so that e.g. a Speed Potion
     * does not match a Strength Potion shop entry.
     */
    @SuppressWarnings("deprecation")
    private boolean matches(ShopItem shopItem, org.bukkit.inventory.ItemStack stack) {
        if (shopItem.getDisplayItem() == null) return false;
        if (shopItem.getDisplayItem().getType() != stack.getType()) return false;

        ItemMeta sMeta = shopItem.getDisplayItem().getItemMeta();
        ItemMeta cMeta = stack.getItemMeta();

        // Potion type matching
        if (sMeta instanceof PotionMeta spm && cMeta instanceof PotionMeta cpm) {
            PotionData sd = spm.getBasePotionData();
            PotionData cd = cpm.getBasePotionData();
            if (sd == null) return true; // no type set on shop item → accept any
            return cd != null && sd.getType() == cd.getType();
        }

        // Enchanted book matching
        if (sMeta instanceof EnchantmentStorageMeta sesm && cMeta instanceof EnchantmentStorageMeta cesm) {
            if (sesm.getStoredEnchants().isEmpty()) return true; // no enchants set → accept any
            return sesm.getStoredEnchants().equals(cesm.getStoredEnchants());
        }

        return true;
    }

    private void copyDefaultShops() {
        String[] defaults = {
            "shops/survival_blocks.yml",
            "shops/survival_food.yml",
            "shops/survival_tools.yml",
            "shops/survival_armor.yml",
            "shops/survival_potions.yml",
            "shops/survival_mob_drops.yml",
            "shops/survival_enchanting.yml",
            "shops/survival_farming.yml",
            "shops/survival_mining.yml",
            "shops/survival_spawners.yml",
            "shops/survival_nether.yml",
            "shops/survival_end.yml",
        };
        for (String path : defaults) {
            try {
                if (plugin.getResource(path) != null) {
                    plugin.saveResource(path, false);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not copy default shop: " + path);
            }
        }
    }
}
