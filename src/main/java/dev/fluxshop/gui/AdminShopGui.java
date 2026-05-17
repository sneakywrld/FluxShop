package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.Shop;
import dev.fluxshop.model.ShopCategory;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game admin editor GUI — three-level browser: Shops → Categories → Items.
 *
 * <p>Level 0 (Shop List): shows all loaded shops; click to drill into a shop.
 * Level 1 (Category List): shows categories in the selected shop; click to view items.
 * Level 2 (Item List): shows items in the selected category; click for item detail.
 *
 * <p>Admin actions available from each level:
 * <ul>
 *   <li>Shift-click a shop/category/item → delete (with a confirmation pass)</li>
 *   <li>Navigation row provides Back / Page← / Page→ / Close</li>
 * </ul>
 *
 * <p>Actual price-editing and item-creation require chat/sign input handled by
 * {@link dev.fluxshop.listener.PlayerListener} — this GUI opens the flow.
 */
public class AdminShopGui extends FluxGui {

    private enum Level { SHOP_LIST, CATEGORY_LIST, ITEM_LIST, ITEM_DETAIL }

    // ── Navigation constants ──────────────────────────────────────────────────
    private static final int PREV_SLOT    = 45;
    private static final int BACK_SLOT    = 49;
    private static final int NEXT_SLOT    = 53;
    private static final int CLOSE_SLOT   = 48;
    private static final int CREATE_SLOT  = 50;
    private static final int RESTOCK_SLOT = 47;

    private static final int[] CONTENT_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    };
    private static final int PAGE_SIZE = CONTENT_SLOTS.length; // 28

    // ── State ─────────────────────────────────────────────────────────────────
    private final FluxShopPlugin plugin;
    private Level    level      = Level.SHOP_LIST;
    private int      page       = 0;
    private Shop     selectedShop;
    private ShopCategory selectedCategory;
    private ShopItem     selectedItem;

    public AdminShopGui(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    @Override
    public void open(Player player) {
        switch (level) {
            case SHOP_LIST     -> openShopList(player);
            case CATEGORY_LIST -> openCategoryList(player);
            case ITEM_LIST     -> openItemList(player);
            case ITEM_DETAIL   -> openItemDetail(player, selectedItem);
        }
    }

    // ── Level 0: Shop List ────────────────────────────────────────────────────

    private void openShopList(Player player) {
        List<Shop> shops = new ArrayList<>(plugin.getShopManager().getAllShops());
        int totalPages   = Math.max(1, (int) Math.ceil(shops.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        GuiBuilder builder = new GuiBuilder(6, "&4&lFluxShop &8» &6All Shops &7(Page " + (page+1) + "/" + totalPages + ")")
            .border(Material.RED_STAINED_GLASS_PANE, " ")
            .fill(Material.GRAY_STAINED_GLASS_PANE, " ");

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= shops.size()) break;
            Shop shop = shops.get(idx);

            List<String> lore = new ArrayList<>();
            lore.add("&7ID: &f" + shop.getId());
            lore.add("&7Categories: &f" + shop.getCategories().size());
            lore.add("&7Permission: &f" + (shop.getPermission() == null || shop.getPermission().isEmpty() ? "(none)" : shop.getPermission()));
            lore.add("&7Status: " + (shop.isEnabled() ? "&aEnabled" : "&cDisabled"));
            lore.add("");
            lore.add("&eLeft-click &7to view categories");
            lore.add("&cShift-click &7to delete");

            Material icon = (shop.getDisplayItem() != null) ? shop.getDisplayItem().getType() : Material.CHEST;
            builder.set(CONTENT_SLOTS[i], new ItemBuilder(icon)
                .name("&6&l" + shop.getDisplayName())
                .lore(lore.toArray(new String[0]))
                .build());
        }

        addNav(builder, page > 0, page < totalPages - 1, false, "&a+ New Shop");
        inventory = builder.build(new ShopHolder(null, null));
        player.openInventory(inventory);
    }

    // ── Level 1: Category List ────────────────────────────────────────────────

    private void openCategoryList(Player player) {
        List<ShopCategory> cats = selectedShop.getCategories();
        int totalPages = Math.max(1, (int) Math.ceil(cats.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        GuiBuilder builder = new GuiBuilder(6,
            "&4&lFluxShop &8» &6" + selectedShop.getDisplayName() + " &8» &eCategories &7(Page " + (page+1) + "/" + totalPages + ")")
            .border(Material.ORANGE_STAINED_GLASS_PANE, " ")
            .fill(Material.GRAY_STAINED_GLASS_PANE, " ");

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= cats.size()) break;
            ShopCategory cat = cats.get(idx);

            List<String> lore = new ArrayList<>();
            lore.add("&7ID: &f" + cat.getId());
            lore.add("&7Items: &f" + cat.getItems().size());
            lore.add("&7Permission: &f" + (cat.getPermission() == null ? "(none)" : cat.getPermission()));
            lore.add("&7Status: " + (cat.isEnabled() ? "&aEnabled" : "&cDisabled"));
            lore.add("");
            lore.add("&eLeft-click &7to view items");
            lore.add("&cShift-click &7to delete");

            Material icon = (cat.getIcon() != null) ? cat.getIcon().getType() : Material.BOOK;
            builder.set(CONTENT_SLOTS[i], new ItemBuilder(icon)
                .name("&e&l" + cat.getDisplayName())
                .lore(lore.toArray(new String[0]))
                .build());
        }

        addNav(builder, page > 0, page < totalPages - 1, true, "&a+ New Category");
        inventory = builder.build(new ShopHolder(selectedShop, null));
        player.openInventory(inventory);
    }

    // ── Level 2: Item List ────────────────────────────────────────────────────

    private void openItemList(Player player) {
        List<ShopItem> items = selectedCategory.getItems();
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        GuiBuilder builder = new GuiBuilder(6,
            "&4&lFluxShop &8» &e" + selectedCategory.getDisplayName() + " &8» &bItems")
            .border(Material.BLUE_STAINED_GLASS_PANE, " ")
            .fill(Material.GRAY_STAINED_GLASS_PANE, " ");

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= items.size()) break;
            ShopItem shopItem = items.get(idx);

            List<String> lore = new ArrayList<>();
            lore.add("&7ID: &f" + shopItem.getId());
            lore.add("&7Buy price: &a" + (shopItem.isBuyable()   ? "$" + shopItem.getBuyPrice()  : "&cnot buyable"));
            lore.add("&7Sell price: &b" + (shopItem.isSellable() ? "$" + shopItem.getSellPrice() : "&cnot sellable"));
            if (shopItem.getGlobalStock() >= 0) lore.add("&7Global stock: &f" + shopItem.getGlobalStock());
            if (shopItem.getPlayerLimit() >= 0)  lore.add("&7Player limit: &f" + shopItem.getPlayerLimit());
            if (shopItem.getBuyPermission() != null) lore.add("&7Buy perm: &f" + shopItem.getBuyPermission());
            lore.add("");
            lore.add("&eLeft-click &7for details");
            lore.add("&cShift-click &7to delete");

            ItemStack display = shopItem.getDisplayItem() != null
                ? shopItem.getDisplayItem().clone()
                : new ItemStack(Material.PAPER);
            builder.set(CONTENT_SLOTS[i], new ItemBuilder(display)
                .name("&b" + (shopItem.getCustomName() != null ? shopItem.getCustomName() : shopItem.getId()))
                .lore(lore.toArray(new String[0]))
                .build());
        }

        // Restock button in nav row
        builder.set(RESTOCK_SLOT, new ItemBuilder(Material.HOPPER)
            .name("&aRestock Category")
            .lore("&7Click to force-restock", "&7all items in this category.")
            .build());

        addNav(builder, page > 0, page < totalPages - 1, true, "&a+ Add Item (from hand)");
        inventory = builder.build(new ShopHolder(selectedShop, selectedCategory));
        player.openInventory(inventory);
    }

    // ── Level 3: Item Detail ──────────────────────────────────────────────────

    private void openItemDetail(Player player, ShopItem item) {
        GuiBuilder builder = new GuiBuilder(6,
            "&4&lFluxShop &8» &bItem: &f" + item.getId())
            .border(Material.CYAN_STAINED_GLASS_PANE, " ")
            .fill(Material.GRAY_STAINED_GLASS_PANE, " ");

        // Item preview — centre
        if (item.getDisplayItem() != null) {
            builder.set(13, new ItemBuilder(item.getDisplayItem().clone())
                .name("&f&l" + (item.getCustomName() != null ? item.getCustomName() : item.getId()))
                .lore(
                    "&7Shop: &f"     + item.getShopId(),
                    "&7Category: &f" + item.getCategoryId(),
                    "&7Material: &f" + item.getDisplayItem().getType().name()
                )
                .build());
        }

        // Edit buy price
        builder.set(19, new ItemBuilder(Material.GOLD_INGOT)
            .name("&6Edit Buy Price")
            .lore(
                "&7Current: &a" + (item.isBuyable() ? "$" + item.getBuyPrice() : "disabled"),
                "",
                "&eClick &7to set a new buy price",
                "&8(Chat input — type the price)")
            .build());

        // Edit sell price
        builder.set(20, new ItemBuilder(Material.EMERALD)
            .name("&aEdit Sell Price")
            .lore(
                "&7Current: &b" + (item.isSellable() ? "$" + item.getSellPrice() : "disabled"),
                "",
                "&eClick &7to set a new sell price",
                "&8(Chat input — type the price)")
            .build());

        // Edit stock
        builder.set(21, new ItemBuilder(Material.HOPPER)
            .name("&bEdit Stock")
            .lore(
                "&7Global stock: &f" + (item.getGlobalStock() < 0 ? "unlimited" : item.getGlobalStock()),
                "&7Player limit: &f" + (item.getPlayerLimit() < 0 ? "unlimited" : item.getPlayerLimit()),
                "",
                "&eClick &7to edit stock settings")
            .build());

        // Force restock
        builder.set(22, new ItemBuilder(Material.REPEATER)
            .name("&eForce Restock")
            .lore("&7Resets purchase counters", "&7for this item.")
            .build());

        // Toggle buyable
        builder.set(23, new ItemBuilder(item.isBuyable() ? Material.LIME_DYE : Material.RED_DYE)
            .name(item.isBuyable() ? "&aBuyable: YES" : "&cBuyable: NO")
            .lore("&eClick &7to toggle")
            .build());

        // Toggle sellable
        builder.set(24, new ItemBuilder(item.isSellable() ? Material.LIME_DYE : Material.RED_DYE)
            .name(item.isSellable() ? "&aSellable: YES" : "&cSellable: NO")
            .lore("&eClick &7to toggle")
            .build());

        // Delete item
        builder.set(34, new ItemBuilder(Material.TNT)
            .name("&c&lDelete Item")
            .lore("&7Removes this item from the shop.", "", "&cThis action cannot be undone.")
            .build());

        addNav(builder, false, false, true, null);
        inventory = builder.build(new ShopHolder(selectedShop, selectedCategory));
        player.openInventory(inventory);
    }

    // ── Click Handler ─────────────────────────────────────────────────────────

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // ── Navigation controls ───────────────────────────────────────────────
        if (slot == CLOSE_SLOT) { player.closeInventory(); return; }

        if (slot == BACK_SLOT) {
            switch (level) {
                case CATEGORY_LIST -> { level = Level.SHOP_LIST;     selectedShop     = null; page = 0; }
                case ITEM_LIST     -> { level = Level.CATEGORY_LIST; selectedCategory = null; page = 0; }
                case ITEM_DETAIL   -> { level = Level.ITEM_LIST;     page = 0; }
                default            -> { player.closeInventory(); return; }
            }
            open(player);
            return;
        }

        if (slot == PREV_SLOT && page > 0)  { page--; open(player); return; }
        if (slot == NEXT_SLOT && hasNextPage()) { page++; open(player); return; }

        // ── Create new ────────────────────────────────────────────────────────
        if (slot == CREATE_SLOT) {
            handleCreate(player);
            return;
        }

        // ── Restock (item list level) ─────────────────────────────────────────
        if (slot == RESTOCK_SLOT && level == Level.ITEM_LIST) {
            if (selectedCategory != null) {
                for (ShopItem si : selectedCategory.getItems()) {
                    new dev.fluxshop.shop.StockManager(plugin).restock(si.getShopId(), si.getId());
                }
                plugin.getMessageManager().sendRaw(player, "&a[FluxShop Editor] &fRestocked all items in &e" + selectedCategory.getDisplayName() + "&f.");
                plugin.getSoundManager().play(player, "purchase-success");
            }
            return;
        }

        // ── Content slot click ────────────────────────────────────────────────
        if (!isContentSlot(slot)) return;

        int idx = contentIndex(slot);

        switch (level) {
            case SHOP_LIST -> {
                List<Shop> shops = new ArrayList<>(plugin.getShopManager().getAllShops());
                int absIdx = page * PAGE_SIZE + idx;
                if (absIdx >= shops.size()) return;
                Shop shop = shops.get(absIdx);

                if (event.isShiftClick()) {
                    handleDeleteShop(player, shop);
                } else {
                    selectedShop = shop;
                    level = Level.CATEGORY_LIST;
                    page  = 0;
                    open(player);
                }
            }
            case CATEGORY_LIST -> {
                List<ShopCategory> cats = selectedShop.getCategories();
                int absIdx = page * PAGE_SIZE + idx;
                if (absIdx >= cats.size()) return;
                ShopCategory cat = cats.get(absIdx);

                if (event.isShiftClick()) {
                    handleDeleteCategory(player, cat);
                } else {
                    selectedCategory = cat;
                    level = Level.ITEM_LIST;
                    page  = 0;
                    open(player);
                }
            }
            case ITEM_LIST -> {
                List<ShopItem> items = selectedCategory.getItems();
                int absIdx = page * PAGE_SIZE + idx;
                if (absIdx >= items.size()) return;
                ShopItem item = items.get(absIdx);

                if (event.isShiftClick()) {
                    handleDeleteItem(player, item);
                } else {
                    selectedItem = item;
                    level = Level.ITEM_DETAIL;
                    openItemDetail(player, item);
                }
            }
            case ITEM_DETAIL -> handleItemDetailClick(player, slot);
        }
    }

    // ── Action Handlers ───────────────────────────────────────────────────────

    private void handleCreate(Player player) {
        switch (level) {
            case SHOP_LIST -> plugin.getMessageManager().sendRaw(player,
                "&e[FluxShop Editor] &fTo create a new shop, add a YAML file to &bplugins/FluxShop/shops/&f and run &a/fluxshop reload&f.");
            case CATEGORY_LIST -> plugin.getMessageManager().sendRaw(player,
                "&e[FluxShop Editor] &fTo add a category, edit &b" + selectedShop.getId() + ".yml&f and run &a/fluxshop reload&f.");
            case ITEM_LIST -> {
                // Add the item in the player's main hand to this category
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType() == Material.AIR) {
                    plugin.getMessageManager().sendRaw(player, "&c[FluxShop Editor] &fHold the item you want to add in your main hand.");
                    return;
                }
                plugin.getMessageManager().sendRaw(player,
                    "&e[FluxShop Editor] &fTo add &b" + hand.getType().name() + "&f to &e"
                    + selectedCategory.getDisplayName() + "&f, add an entry to the category in the YAML and run &a/fluxshop reload&f.");
                plugin.getMessageManager().sendRaw(player, "&7Tip: &fItem section template:");
                plugin.getMessageManager().sendRaw(player, "&8  - id: " + hand.getType().name().toLowerCase());
                plugin.getMessageManager().sendRaw(player, "&8    material: " + hand.getType().name());
                plugin.getMessageManager().sendRaw(player, "&8    buy-price: 100.0");
                plugin.getMessageManager().sendRaw(player, "&8    sell-price: 50.0");
            }
            default -> {}
        }
    }

    private void handleDeleteShop(Player player, Shop shop) {
        plugin.getMessageManager().sendRaw(player, "&c[FluxShop Editor] &fTo delete shop &e" + shop.getId()
            + "&f, remove or rename &b" + shop.getId() + ".yml&f and run &a/fluxshop reload&f.");
    }

    private void handleDeleteCategory(Player player, ShopCategory cat) {
        plugin.getMessageManager().sendRaw(player, "&c[FluxShop Editor] &fTo delete category &e" + cat.getId()
            + "&f, remove it from the shop YAML and run &a/fluxshop reload&f.");
    }

    private void handleDeleteItem(Player player, ShopItem item) {
        plugin.getMessageManager().sendRaw(player, "&c[FluxShop Editor] &fTo delete item &e" + item.getId()
            + "&f, remove it from the category YAML and run &a/fluxshop reload&f.");
    }

    private void handleItemDetailClick(Player player, int slot) {
        // Force restock
        if (slot == 22 && selectedItem != null) {
            new dev.fluxshop.shop.StockManager(plugin).restock(selectedItem.getShopId(), selectedItem.getId());
            plugin.getMessageManager().sendRaw(player, "&a[FluxShop Editor] &fRestocked &e" + selectedItem.getId() + "&f.");
            plugin.getSoundManager().play(player, "purchase-success");
        }
    }

    // ── Navigation Builder ────────────────────────────────────────────────────

    private void addNav(GuiBuilder builder, boolean hasPrev, boolean hasNext,
                        boolean hasBack, String createLabel) {
        // Previous page
        builder.set(PREV_SLOT, new ItemBuilder(hasPrev ? Material.ARROW : Material.GRAY_DYE)
            .name(hasPrev ? "&ePrevious Page" : "&8No Previous Page")
            .build());

        // Back button
        if (hasBack) {
            builder.set(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name("&cBack")
                .lore("&7Return to previous screen.")
                .build());
        } else {
            builder.set(BACK_SLOT, new ItemBuilder(Material.BARRIER)
                .name("&c&lClose")
                .lore("&7Close the editor.")
                .build());
        }

        // Close
        builder.set(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
            .name("&c&lClose Editor")
            .build());

        // Next page
        builder.set(NEXT_SLOT, new ItemBuilder(hasNext ? Material.ARROW : Material.GRAY_DYE)
            .name(hasNext ? "&eNext Page" : "&8No Next Page")
            .build());

        // Create
        if (createLabel != null) {
            builder.set(CREATE_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name(createLabel)
                .lore("&7Click for instructions.")
                .build());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns true if there is a next page for the current level and page. */
    private boolean hasNextPage() {
        int total = switch (level) {
            case SHOP_LIST     -> plugin.getShopManager().getAllShops().size();
            case CATEGORY_LIST -> selectedShop != null ? selectedShop.getCategories().size() : 0;
            case ITEM_LIST     -> selectedCategory != null ? selectedCategory.getItems().size() : 0;
            default            -> 0;
        };
        return (page + 1) * PAGE_SIZE < total;
    }

    private boolean isContentSlot(int slot) {
        for (int s : CONTENT_SLOTS) if (s == slot) return true;
        return false;
    }

    private int contentIndex(int slot) {
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slot) return i;
        }
        return -1;
    }
}
