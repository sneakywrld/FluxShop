package dev.fluxshop.shop;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.Shop;
import dev.fluxshop.model.ShopCategory;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * Parses shop YAML files into Shop / ShopCategory / ShopItem model objects.
 * All errors are logged as warnings — invalid items are skipped, not fatal.
 */
public class ShopLoader {

    private final FluxShopPlugin plugin;
    private final Logger         log;

    public ShopLoader(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    /**
     * Load a single shop file into a {@link Shop}.
     *
     * @param file the .yml file to parse
     * @return the loaded Shop, or empty if the file is invalid or disabled
     */
    public Optional<Shop> load(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        if (!cfg.getBoolean("enabled", true)) return Optional.empty();

        String shopId = cfg.getString("id");
        if (shopId == null || shopId.isBlank()) {
            shopId = file.getName().replace(".yml", "");
        }

        Shop shop = new Shop();
        shop.setId(shopId);
        shop.setDisplayName(cfg.getString("display-name", shopId));
        shop.setEconomyProvider(cfg.getString("economy", "default"));
        shop.setPermission(cfg.getString("permission", ""));
        shop.setDisplaySlot(cfg.getInt("display-slot", -1));
        shop.setAllowedWorlds(cfg.getStringList("allowed-worlds"));
        shop.setBlockedWorlds(cfg.getStringList("blocked-worlds"));
        shop.setEnabled(true);

        ItemStack displayItem = parseDisplayItem(cfg.getConfigurationSection("display-item"), shopId + ".display-item");
        shop.setDisplayItem(displayItem != null ? displayItem : new ItemStack(Material.CHEST));

        ConfigurationSection catsSec = cfg.getConfigurationSection("categories");
        if (catsSec != null) {
            for (String catKey : catsSec.getKeys(false)) {
                ConfigurationSection catSec = catsSec.getConfigurationSection(catKey);
                if (catSec == null) continue;
                ShopCategory category = parseCategory(catSec, shopId, catKey);
                if (category != null) shop.addCategory(category);
            }
        }

        return Optional.of(shop);
    }

    private ShopCategory parseCategory(ConfigurationSection sec, String shopId, String catId) {
        if (!sec.getBoolean("enabled", true)) return null;

        ShopCategory cat = new ShopCategory();
        cat.setId(catId);
        cat.setShopId(shopId);
        cat.setDisplayName(sec.getString("display-name", catId));
        cat.setPermission(sec.getString("permission", ""));
        cat.setEconomyProvider(sec.getString("economy", "default"));
        cat.setFillMaterial(sec.getString("fill-material", "BLACK_STAINED_GLASS_PANE"));
        cat.setBorderMaterial(sec.getString("border-material", "GRAY_STAINED_GLASS_PANE"));
        cat.setRows(sec.getInt("rows", 6));
        cat.setIconSlot(sec.getInt("icon-slot", -1));
        cat.setHidden(sec.getBoolean("hidden", false));
        cat.setPageTitle(sec.getString("page-title", cat.getDisplayName()));

        ItemStack icon = parseDisplayItem(sec.getConfigurationSection("icon"), shopId + "/" + catId + ".icon");
        cat.setIcon(icon != null ? icon : new ItemStack(Material.BOOK));

        ConfigurationSection itemsSec = sec.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String itemKey : itemsSec.getKeys(false)) {
                ConfigurationSection itemSec = itemsSec.getConfigurationSection(itemKey);
                if (itemSec == null) continue;
                ShopItem item = parseShopItem(itemSec, shopId, catId, itemKey);
                if (item != null) cat.addItem(item);
            }
        }

        return cat;
    }

    private ShopItem parseShopItem(ConfigurationSection sec, String shopId, String catId, String itemId) {
        if (!sec.getBoolean("enabled", true)) return null;

        // ── Material / custom item ──
        String material    = sec.getString("material", "STONE");
        String mythicId    = sec.getString("mythic-item", null);
        String itemsAddId  = sec.getString("items-adder", null);
        String oraxenId    = sec.getString("oraxen", null);
        // Accept both "spawner-entity" (documented key) and "spawner-type" (YAML default shops use this)
        String spawnerType = sec.getString("spawner-entity", sec.getString("spawner-type", null));

        ItemStack displayItem = null;

        if (mythicId != null) {
            displayItem = plugin.getCompatManager().getMythicItem(mythicId);
        } else if (itemsAddId != null) {
            displayItem = plugin.getCompatManager().getItemsAdderItem(itemsAddId);
        } else if (oraxenId != null) {
            displayItem = plugin.getCompatManager().getOraxenItem(oraxenId);
        } else if (spawnerType != null) {
            var spawnerProvider = plugin.getCompatManager().getSpawnerProvider();
            if (spawnerProvider == null) {
                log.warning("[ShopLoader] No spawner plugin found — skipping spawner item '" + spawnerType + "' in " + shopId + "/" + catId + "/" + itemId);
            } else {
                try {
                    displayItem = spawnerProvider.createSpawnerItem(spawnerType.toUpperCase());
                } catch (Exception e) {
                    log.warning("[ShopLoader] Bad spawner entity '" + spawnerType + "' in " + shopId + "/" + catId + "/" + itemId);
                }
            }
        }

        if (displayItem == null) {
            Material mat = parseMaterial(material, shopId + "/" + catId + "/" + itemId);
            if (mat == null) return null;
            displayItem = new ItemStack(mat);
        }

        // Apply custom name/lore
        String customName       = sec.getString("name", null);
        List<String> customLore = sec.getStringList("lore");

        ItemBuilder builder = new ItemBuilder(displayItem.clone());
        if (customName != null && !customName.isBlank()) builder.name(customName);
        if (!customLore.isEmpty()) builder.lore(customLore);
        displayItem = builder.build();

        // ── Potion type (POTION / SPLASH_POTION / LINGERING_POTION) ──
        String potionType = sec.getString("potion-type", null);
        if (potionType != null) {
            applyPotionType(displayItem, potionType, shopId + "/" + catId + "/" + itemId);
        }

        // ── Enchanted book stored enchantment ──
        String bookEnchant = sec.getString("book-enchant", null);
        if (bookEnchant != null) {
            int bookLevel = sec.getInt("book-enchant-level", 1);
            applyBookEnchant(displayItem, bookEnchant, bookLevel, shopId + "/" + catId + "/" + itemId);
        }

        // Enchantments are parsed below and applied to displayItem after the map is built
        // (displayItem reference is updated again after this block)

        // ── Prices ──
        double buyPrice  = sec.getDouble("buy-price",  -1);
        double sellPrice = sec.getDouble("sell-price", -1);
        String economy   = sec.getString("economy", null);

        // ── Permissions ──
        String buyPerm  = sec.getString("buy-permission",  "");
        String sellPerm = sec.getString("sell-permission", "");

        // ── Stock ──
        int globalStock      = sec.getInt("global-stock",             -1);
        int playerLimit      = sec.getInt("player-limit",             -1);
        int playerLimitReset = sec.getInt("player-limit-reset-seconds", 86400);
        int autoRestock      = sec.getInt("auto-restock-seconds",     -1);

        // ── Dynamic pricing ──
        boolean dynamicPricing   = sec.getBoolean("dynamic-pricing",    false);
        double  dynamicMultiplier = sec.getDouble("dynamic-multiplier", 0.1);

        // ── Seasonal modifiers ──
        Map<String, Double> seasonModifiers = new HashMap<>();
        ConfigurationSection seasons = sec.getConfigurationSection("season-modifiers");
        if (seasons != null) {
            for (String season : seasons.getKeys(false)) {
                seasonModifiers.put(season.toUpperCase(), seasons.getDouble(season, 1.0));
            }
        }

        // ── Commands ──
        List<String> onBuy  = sec.getStringList("on-buy-commands");
        List<String> onSell = sec.getStringList("on-sell-commands");

        // ── Enchantments ──
        Map<String, Integer> enchants = new HashMap<>();
        ConfigurationSection enchSec = sec.getConfigurationSection("enchantments");
        if (enchSec != null) {
            for (String k : enchSec.getKeys(false)) enchants.put(k.toUpperCase(), enchSec.getInt(k, 1));
        }
        boolean allowUnsafe = sec.getBoolean("allow-unsafe-enchants", false);

        // Apply vanilla enchantments to displayItem; custom enchants handled at runtime
        if (!enchants.isEmpty()) {
            displayItem = applyVanillaEnchantments(displayItem, enchants, allowUnsafe, shopId + "/" + catId + "/" + itemId);
            plugin.getCompatManager().applyCustomEnchantments(displayItem, enchants, allowUnsafe);
        }

        // ── GUI ──
        int     guiSlot  = sec.getInt("slot", -1);
        boolean dispOnly = sec.getBoolean("display-only", false);

        // ── Build object ──
        ShopItem item = new ShopItem();
        item.setId(itemId);
        item.setShopId(shopId);
        item.setCategoryId(catId);
        item.setDisplayItem(displayItem);
        item.setCustomName(customName);
        item.setCustomLore(customLore);
        item.setBuyPrice(buyPrice);
        item.setSellPrice(sellPrice);
        item.setEconomyProvider(economy);
        item.setMythicItemId(mythicId);
        item.setItemsAdderId(itemsAddId);
        item.setOraxenId(oraxenId);
        item.setSpawnerEntityType(spawnerType);
        item.setOnBuyCommands(onBuy);
        item.setOnSellCommands(onSell);
        item.setBuyPermission(buyPerm);
        item.setSellPermission(sellPerm);
        item.setGlobalStock(globalStock);
        item.setPlayerLimit(playerLimit);
        item.setPlayerLimitResetSeconds(playerLimitReset);
        item.setAutoRestockSeconds(autoRestock);
        item.setDynamicPricingEnabled(dynamicPricing);
        item.setDynamicMultiplier(dynamicMultiplier);
        item.setSeasonModifiers(seasonModifiers);
        item.setEnchantments(enchants);
        item.setAllowUnsafeEnchants(allowUnsafe);
        item.setGuiSlot(guiSlot);
        item.setDisplayOnly(dispOnly);

        return item;
    }

    /** Parse a display-item config section into an ItemStack (for icons). */
    private ItemStack parseDisplayItem(ConfigurationSection sec, String context) {
        if (sec == null) return null;

        String matName = sec.getString("material", "STONE");
        Material mat   = parseMaterial(matName, context);
        if (mat == null) return null;

        ItemBuilder builder = new ItemBuilder(new ItemStack(mat));

        String name = sec.getString("name", null);
        if (name != null) builder.name(name);

        List<String> lore = sec.getStringList("lore");
        if (!lore.isEmpty()) builder.lore(lore);

        int cmd = sec.getInt("custom-model-data", 0);
        if (cmd > 0) builder.customModelData(cmd);

        String skull = sec.getString("skull-texture", null);
        if (skull != null) builder.skull(skull);

        return builder.build();
    }

    /**
     * Matches an enchantment by legacy (pre-1.13) name like "DAMAGE_ALL".
     * The {@code getName()} method is deprecated-for-removal in newer API versions
     * but must remain for 1.8–1.12 cross-version compat.
     */
    @SuppressWarnings({"deprecation", "removal"})
    private Enchantment findEnchantByLegacyName(String key) {
        for (Enchantment e : Enchantment.values()) {
            if (e.getKey().getKey().equalsIgnoreCase(key) || e.getName().equalsIgnoreCase(key)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Applies a potion type to a POTION / SPLASH_POTION / LINGERING_POTION item.
     * Uses the deprecated {@code setBasePotionData} API which is stable across 1.9–1.21.
     */
    @SuppressWarnings("deprecation")
    private void applyPotionType(ItemStack item, String typeName, String context) {
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return;
        try {
            PotionType type = PotionType.valueOf(typeName.toUpperCase());
            meta.setBasePotionData(new PotionData(type));
            item.setItemMeta(meta);
        } catch (IllegalArgumentException e) {
            log.warning("[ShopLoader] Unknown potion-type '" + typeName + "' at " + context);
        }
    }

    /**
     * Applies a stored enchantment to an ENCHANTED_BOOK item.
     */
    private void applyBookEnchant(ItemStack item, String enchantKey, int level, String context) {
        if (!(item.getItemMeta() instanceof EnchantmentStorageMeta meta)) return;
        Enchantment enc = Enchantment.getByKey(NamespacedKey.minecraft(enchantKey.toLowerCase()));
        if (enc == null) enc = findEnchantByLegacyName(enchantKey);
        if (enc == null) {
            log.warning("[ShopLoader] Unknown book-enchant '" + enchantKey + "' at " + context);
            return;
        }
        meta.addStoredEnchant(enc, level, true);
        item.setItemMeta(meta);
    }

    private Material parseMaterial(String name, String context) {
        if (name == null || name.isBlank()) return Material.STONE;
        Material mat = Material.matchMaterial(name);
        if (mat == null) {
            log.warning("[ShopLoader] Unknown material '" + name + "' at " + context + " — skipping");
            return null;
        }
        return mat;
    }

    /**
     * Applies vanilla enchantments to an ItemStack.
     * Unknown keys are silently skipped — they may be custom enchants handled elsewhere.
     */
    private ItemStack applyVanillaEnchantments(ItemStack stack, Map<String, Integer> enchants,
                                                boolean unsafe, String context) {
        ItemStack result = stack.clone();
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            String key   = entry.getKey().toLowerCase();
            int    level = entry.getValue();
            // Try modern NamespacedKey lookup first
            Enchantment enc = Enchantment.getByKey(NamespacedKey.minecraft(key));
            // Fall back to legacy name match (1.12 and below names like "DAMAGE_ALL")
            if (enc == null) enc = findEnchantByLegacyName(key);
            if (enc != null) {
                if (unsafe) {
                    result.addUnsafeEnchantment(enc, level);
                } else {
                    try {
                        result.addEnchantment(enc, level);
                    } catch (IllegalArgumentException e) {
                        log.warning("[ShopLoader] Enchant " + key + " lvl " + level
                            + " rejected at " + context + " (use allow-unsafe-enchants: true to force)");
                    }
                }
            }
            // If enc is still null, it's a custom enchant — CompatManager.applyCustomEnchantments() handles it
        }
        return result;
    }
}
