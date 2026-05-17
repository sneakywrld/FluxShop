package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.Shop;
import dev.fluxshop.model.ShopCategory;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The main shop menu.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>All-shops view</b> — constructed with a list of all shops; shows every shop
 *       as a clickable icon. Clicking a shop with one category jumps directly into it;
 *       clicking a multi-category shop opens a category picker.</li>
 *   <li><b>Category picker</b> — constructed with a single shop; shows that shop's
 *       categories as icons. Clicking a category opens {@link CategoryGui}.</li>
 * </ul>
 *
 * Layout, border, background, and button config are all driven by {@code guis/main_menu.yml}.
 */
public class MainMenuGui extends FluxGui {

    private final FluxShopPlugin plugin;

    /** Non-null in all-shops view; null in category-picker view. */
    private final List<Shop> allShops;
    /** Non-null in category-picker view; null in all-shops view. */
    private final Shop shop;

    // Slots resolved from config on every open() call, used by handleClick().
    private int[] activeSlots;
    private int   activeCloseSlot = 49;
    private int   activeBackSlot  = -1; // only shown in category-picker view

    // ── Default slot grids ─────────────────────────────────────────────────────

    /** 3 × 4 centered grid — fits up to 12 shops comfortably. */
    private static final int[] DEFAULT_ALL_SHOPS_SLOTS = {
        11, 12, 13, 14,
        20, 21, 22, 23,
        29, 30, 31, 32
    };

    /** 4 × 7 grid — fits up to 28 categories per shop. */
    private static final int[] DEFAULT_CATEGORY_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private static final int[] BEDROCK_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    // ── Constructors ──────────────────────────────────────────────────────────

    /** All-shops view — shows every shop as a clickable icon. */
    public MainMenuGui(FluxShopPlugin plugin, List<Shop> allShops) {
        this.plugin   = plugin;
        this.allShops = new ArrayList<>(allShops);
        this.shop     = null;
    }

    /** Category-picker view — shows one shop's categories. */
    public MainMenuGui(FluxShopPlugin plugin, Shop shop) {
        this.plugin   = plugin;
        this.shop     = shop;
        this.allShops = null;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    @Override
    public void open(Player player) {
        FileConfiguration cfg = plugin.getConfigManager().getGuiConfig("main_menu");
        boolean bedrock = plugin.getCompatManager().isBedrockPlayer(player);

        String rawTitle = allShops != null
            ? cfg.getString("title", "&6&l✦ Server Shop ✦")
            : cfg.getString("category-title", "&7« &6&l{shop} &7»")
                .replace("{shop}", shop.getDisplayName());

        String title = plugin.getMessageManager().format(rawTitle, player);

        int      rows      = bedrock ? 5 : cfg.getInt("rows", 6);
        Material borderMat = safeMaterial(cfg.getString("border.material"), Material.GRAY_STAINED_GLASS_PANE);
        Material bgMat     = safeMaterial(cfg.getString("background.material"), Material.LIGHT_GRAY_STAINED_GLASS_PANE);

        // Resolve active slots
        if (bedrock) {
            activeSlots = BEDROCK_SLOTS;
        } else if (allShops != null) {
            List<Integer> cfgSlots = cfg.getIntegerList("layout.all-shops-slots");
            activeSlots = cfgSlots.isEmpty()
                ? DEFAULT_ALL_SHOPS_SLOTS
                : cfgSlots.stream().mapToInt(Integer::intValue).toArray();
        } else {
            List<Integer> cfgSlots = cfg.getIntegerList("layout.available-slots");
            activeSlots = cfgSlots.isEmpty()
                ? DEFAULT_CATEGORY_SLOTS
                : cfgSlots.stream().mapToInt(Integer::intValue).toArray();
        }

        GuiBuilder builder = new GuiBuilder(rows, title)
            .border(borderMat, " ")
            .fill(bgMat, " ");

        // Divider bar above bottom row
        String   divMatStr = cfg.getString("decorations.divider.material");
        Material divMat    = safeMaterial(divMatStr, borderMat);
        if (bedrock) {
            for (int s = 36; s <= 44; s++) builder.set(s, ItemBuilder.pane(divMat, " "));
        } else {
            for (int s : cfg.getIntegerList("decorations.divider.slots")) {
                builder.set(s, ItemBuilder.pane(divMat, " "));
            }
        }

        // Close button
        activeCloseSlot = bedrock ? 40 : cfg.getInt("decorations.close.slot", 49);
        Material closeMat  = safeMaterial(cfg.getString("decorations.close.material"), Material.BARRIER);
        String   closeName = cfg.getString("decorations.close.name", "&c&l✕ Close");
        List<String> closeLore = cfg.getStringList("decorations.close.lore");
        ItemBuilder closeBtn = new ItemBuilder(closeMat)
            .name(plugin.getMessageManager().format(closeName, player))
            .hideAll();
        if (!closeLore.isEmpty()) {
            closeBtn.lore(closeLore.stream()
                .map(l -> plugin.getMessageManager().format(l, player))
                .collect(Collectors.toList()));
        }
        builder.set(activeCloseSlot, closeBtn.build());

        // Back button (category-picker view only)
        activeBackSlot = -1;
        if (allShops == null && !bedrock) {
            int backSlot = cfg.getInt("decorations.back.slot", 45);
            String backMatStr = cfg.getString("decorations.back.material");
            Material backMat  = safeMaterial(backMatStr, Material.ARROW);
            String   backName = cfg.getString("decorations.back.name", "&7← Back");
            builder.set(backSlot, new ItemBuilder(backMat)
                .name(plugin.getMessageManager().format(backName, player))
                .hideAll()
                .build());
            activeBackSlot = backSlot;
        }

        // Place icons
        if (allShops != null) {
            placeShopIcons(builder, player);
        } else {
            placeCategoryIcons(builder, player);
        }

        inventory = builder.build(new ShopHolder(allShops != null ? null : shop, null));
        player.openInventory(inventory);
        plugin.getSoundManager().play(player, "shop-open");
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == activeCloseSlot) {
            player.closeInventory();
            return;
        }

        if (slot == activeBackSlot && activeBackSlot != -1) {
            // Go back to all-shops view
            List<Shop> all = new ArrayList<>(plugin.getShopManager().getAllShops());
            plugin.getGuiManager().open(player, new MainMenuGui(plugin, all));
            return;
        }

        if (allShops != null) {
            handleShopClick(player, slot);
        } else {
            handleCategoryClick(player, slot);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void placeShopIcons(GuiBuilder builder, Player player) {
        List<Shop> sorted = allShops.stream()
            .filter(s -> s.isEnabled())
            .sorted(Comparator.comparingInt(s -> s.getDisplaySlot() >= 0 ? s.getDisplaySlot() : Integer.MAX_VALUE))
            .collect(Collectors.toList());

        int autoIdx = 0;
        for (Shop s : sorted) {
            if (autoIdx >= activeSlots.length) break;
            int slot = (s.getDisplaySlot() >= 0 && s.getDisplaySlot() < inventory.getSize())
                ? s.getDisplaySlot()
                : activeSlots[autoIdx];
            autoIdx++;
            builder.set(slot, buildShopIcon(s, player));
        }
    }

    private void placeCategoryIcons(GuiBuilder builder, Player player) {
        List<ShopCategory> categories = shop.getCategories();
        int autoIdx = 0;
        for (ShopCategory cat : categories) {
            if (!cat.isEnabled() || cat.isHidden()) continue;
            if (autoIdx >= activeSlots.length) break;
            int slot = cat.getIconSlot() >= 0 ? cat.getIconSlot() : activeSlots[autoIdx];
            autoIdx++;
            if (cat.getPermission() != null && !player.hasPermission(cat.getPermission())) {
                builder.set(slot, buildLockedIcon(cat, player));
            } else {
                builder.set(slot, buildCategoryIcon(cat, player));
            }
        }
    }

    private void handleShopClick(Player player, int slot) {
        List<Shop> sorted = allShops.stream()
            .filter(Shop::isEnabled)
            .sorted(Comparator.comparingInt(s -> s.getDisplaySlot() >= 0 ? s.getDisplaySlot() : Integer.MAX_VALUE))
            .collect(Collectors.toList());

        int autoIdx = 0;
        for (Shop s : sorted) {
            if (autoIdx >= activeSlots.length) break;
            int shopSlot = (s.getDisplaySlot() >= 0 && s.getDisplaySlot() < inventory.getSize())
                ? s.getDisplaySlot()
                : activeSlots[autoIdx];
            autoIdx++;
            if (slot != shopSlot) continue;

            // Permission check on shop
            if (s.getPermission() != null && !player.hasPermission(s.getPermission())) {
                plugin.getMessageManager().send(player, "shop.no-access");
                plugin.getSoundManager().play(player, "purchase-fail");
                return;
            }

            List<ShopCategory> cats = s.getCategories().stream()
                .filter(c -> c.isEnabled() && !c.isHidden())
                .collect(Collectors.toList());

            if (cats.size() == 1) {
                // Skip category picker — go straight to the items
                ShopCategory cat = cats.get(0);
                if (cat.getPermission() != null && !player.hasPermission(cat.getPermission())) {
                    plugin.getMessageManager().send(player, "shop.no-access");
                    plugin.getSoundManager().play(player, "purchase-fail");
                    return;
                }
                plugin.getGuiManager().open(player, new CategoryGui(plugin, s, cat, 0));
            } else {
                plugin.getGuiManager().open(player, new MainMenuGui(plugin, s));
            }
            return;
        }
    }

    private void handleCategoryClick(Player player, int slot) {
        List<ShopCategory> categories = shop.getCategories();
        int autoIdx = 0;
        for (ShopCategory cat : categories) {
            if (!cat.isEnabled() || cat.isHidden()) continue;
            if (autoIdx >= activeSlots.length) break;
            int catSlot = cat.getIconSlot() >= 0 ? cat.getIconSlot() : activeSlots[autoIdx];
            autoIdx++;
            if (slot != catSlot) continue;

            if (cat.getPermission() != null && !player.hasPermission(cat.getPermission())) {
                plugin.getMessageManager().send(player, "shop.no-access");
                plugin.getSoundManager().play(player, "purchase-fail");
                return;
            }
            plugin.getGuiManager().open(player, new CategoryGui(plugin, shop, cat, 0));
            return;
        }
    }

    // ── Icon builders ─────────────────────────────────────────────────────────

    private ItemStack buildShopIcon(Shop shop, Player player) {
        ItemStack base = shop.getDisplayItem() != null
            ? shop.getDisplayItem().clone()
            : new ItemStack(Material.CHEST);

        // Count items across all categories
        List<ShopCategory> cats = shop.getCategories();
        int totalItems    = cats.stream().mapToInt(c -> c.getItems().size()).sum();
        long totalBuyable = cats.stream().flatMap(c -> c.getItems().stream()).filter(ShopItem::isBuyable).count();
        long totalSellable= cats.stream().flatMap(c -> c.getItems().stream()).filter(ShopItem::isSellable).count();

        String name = plugin.getMessageManager().format(
            "&6&l" + shop.getDisplayName(), player);

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessageManager().format("&8─────────────────────", player));
        if (cats.size() > 1) {
            lore.add(plugin.getMessageManager().format(
                "&7" + cats.size() + " categories  •  " + totalItems + " items", player));
        } else {
            lore.add(plugin.getMessageManager().format(
                "&7" + totalItems + " items available", player));
        }
        lore.add(plugin.getMessageManager().format(
            "&a▲ Buy: " + totalBuyable + "  &6▼ Sell: " + totalSellable, player));
        lore.add("");
        lore.add(plugin.getMessageManager().format("&e▶ Click to browse", player));

        return new ItemBuilder(base).name(name).lore(lore).hideAll().build();
    }

    private ItemStack buildCategoryIcon(ShopCategory cat, Player player) {
        ItemStack base = cat.getIcon() != null ? cat.getIcon() : new ItemStack(Material.CHEST);
        String name = plugin.getMessageManager().format(
            "&6&l" + cat.getDisplayName(), player);

        long buyCount  = cat.getItems().stream().filter(ShopItem::isBuyable).count();
        long sellCount = cat.getItems().stream().filter(ShopItem::isSellable).count();

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessageManager().format("&8─────────────────────", player));
        lore.add(plugin.getMessageManager().format(
            "&7" + cat.getItems().size() + " items available", player));
        lore.add(plugin.getMessageManager().format(
            "&a▲ Buy: " + buyCount + "  &6▼ Sell: " + sellCount, player));
        lore.add("");
        lore.add(plugin.getMessageManager().format("&e▶ Click to browse", player));

        return new ItemBuilder(base).name(name).lore(lore).hideAll().build();
    }

    private ItemStack buildLockedIcon(ShopCategory cat, Player player) {
        return new ItemBuilder(Material.BARRIER)
            .name(plugin.getMessageManager().format("&c&l✕ " + cat.getDisplayName(), player))
            .lore(
                plugin.getMessageManager().format("&7You don't have permission", player),
                plugin.getMessageManager().format("&7to access this category.", player)
            )
            .hideAll()
            .build();
    }

    private static Material safeMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }
}
