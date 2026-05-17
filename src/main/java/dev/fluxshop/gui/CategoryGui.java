package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.Shop;
import dev.fluxshop.model.ShopCategory;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.shop.PriceCalculator;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Paginated category browsing GUI.
 * All layout (border, background, navigation buttons, item slots) is driven by guis/category.yml.
 */
public class CategoryGui extends FluxGui {

    // Static fallback slot layout (used when config is missing item-slots)
    private static final int[] DEFAULT_ITEM_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    };

    // Bedrock (GeyserMC): 5-row version — rows 1–3 for items, row 4 for navigation
    private static final int[] BEDROCK_ITEM_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34
    };

    private final FluxShopPlugin plugin;
    private final Shop           shop;
    private final ShopCategory   category;
    private final int            page;
    private final PriceCalculator priceCalc;

    /** Items on the current page — populated in open(), used in handleClick(). */
    private final List<ShopItem> pageItems = new ArrayList<>();
    /** Item slots for the current page — set in open(), read in handleClick(). */
    private int[] currentItemSlots = DEFAULT_ITEM_SLOTS;

    // Navigation slot cache (set in open, read in handleClick)
    private int     backSlot    = 47;
    private int     prevSlot    = 45;
    private int     nextSlot    = 53;
    private int     totalPages  = 1;
    private int     clampedPage = 0;
    /** Whether the viewing player is on Bedrock — cached from open() for handleClick(). */
    private boolean bedrockPlayer = false;

    public CategoryGui(FluxShopPlugin plugin, Shop shop, ShopCategory category, int page) {
        this.plugin    = plugin;
        this.shop      = shop;
        this.category  = category;
        this.page      = page;
        this.priceCalc = new PriceCalculator(plugin);
    }

    @Override
    public void open(Player player) {
        FileConfiguration cfg = plugin.getConfigManager().getGuiConfig("category");
        bedrockPlayer = plugin.getCompatManager().isBedrockPlayer(player);
        boolean bedrock = bedrockPlayer;

        // Bedrock players get a 5-row GUI with fewer item slots per page
        if (bedrock) {
            currentItemSlots = BEDROCK_ITEM_SLOTS;
        } else {
            List<Integer> configSlots = cfg.getIntegerList("item-slots");
            currentItemSlots = configSlots.isEmpty() ? DEFAULT_ITEM_SLOTS
                : configSlots.stream().mapToInt(i -> i).toArray();
        }
        int itemsPerPage = currentItemSlots.length;

        // Filter and paginate
        List<ShopItem> all = category.getItems().stream()
            .filter(i -> i.isBuyable() || i.isSellable())
            .toList();

        totalPages  = Math.max(1, (int) Math.ceil((double) all.size() / itemsPerPage));
        clampedPage = Math.max(0, Math.min(page, totalPages - 1));
        int start   = clampedPage * itemsPerPage;
        int end     = Math.min(start + itemsPerPage, all.size());

        pageItems.clear();
        pageItems.addAll(all.subList(start, end));

        // Title
        String titleTemplate = cfg.getString("title", "&6&l{category}");
        String title = plugin.getMessageManager().format(
            titleTemplate.replace("{category}", category.getDisplayName()), player);

        // Bedrock: 5-row max; Java: use config value
        int      rows      = bedrock ? 5 : cfg.getInt("rows", 6);
        Material borderMat = safeMaterial(cfg.getString("border.material"),     Material.BLACK_STAINED_GLASS_PANE);
        Material bgMat     = safeMaterial(cfg.getString("background.material"), Material.GRAY_STAINED_GLASS_PANE);

        GuiBuilder builder = new GuiBuilder(rows, title)
            .border(borderMat, " ")
            .fill(bgMat, " ");

        // Place items
        for (int i = 0; i < pageItems.size(); i++) {
            builder.set(currentItemSlots[i], buildItemIcon(pageItems.get(i), player));
        }

        // ── Navigation ────────────────────────────────────────────────────────
        // Bedrock: navigation row is row 4 (slots 36-44); Java: row 5 (slots 45-53)

        // Prev page
        prevSlot = bedrock ? 36 : cfg.getInt("navigation.previous-page.slot", 45);
        if (clampedPage > 0) {
            Material prevMat  = safeMaterial(cfg.getString("navigation.previous-page.material"), Material.ARROW);
            String   prevName = formatNav(cfg.getString("navigation.previous-page.name",
                "&b&l◀ Previous Page"), clampedPage, totalPages, all.size());
            List<String> prevLore = formatNavLore(cfg.getStringList("navigation.previous-page.lore"),
                clampedPage, totalPages, all.size());
            ItemBuilder btn = new ItemBuilder(prevMat).name(plugin.getMessageManager().format(prevName, player));
            if (!prevLore.isEmpty()) btn.lore(prevLore.stream().map(l -> plugin.getMessageManager().format(l, player)).collect(Collectors.toList()));
            builder.set(prevSlot, btn.build());
        }

        // Page indicator
        int pageIndicatorSlot = bedrock ? 40 : cfg.getInt("navigation.page-indicator.slot", 49);
        Material pageMat  = safeMaterial(cfg.getString("navigation.page-indicator.material"), Material.PAPER);
        String   pageName = formatNav(cfg.getString("navigation.page-indicator.name",
            "&fPage &e{page}&f/&e{total}"), clampedPage, totalPages, all.size());
        List<String> pageLore = formatNavLore(cfg.getStringList("navigation.page-indicator.lore"),
            clampedPage, totalPages, all.size());
        ItemBuilder pageBtn = new ItemBuilder(pageMat).name(plugin.getMessageManager().format(pageName, player));
        if (!pageLore.isEmpty()) pageBtn.lore(pageLore.stream().map(l -> plugin.getMessageManager().format(l, player)).collect(Collectors.toList()));
        builder.set(pageIndicatorSlot, pageBtn.build());

        // Next page
        nextSlot = bedrock ? 44 : cfg.getInt("navigation.next-page.slot", 53);
        if (clampedPage < totalPages - 1) {
            Material nextMat  = safeMaterial(cfg.getString("navigation.next-page.material"), Material.ARROW);
            String   nextName = formatNav(cfg.getString("navigation.next-page.name",
                "&b&lNext Page ▶"), clampedPage + 1, totalPages, all.size());
            List<String> nextLore = formatNavLore(cfg.getStringList("navigation.next-page.lore"),
                clampedPage + 1, totalPages, all.size());
            ItemBuilder btn = new ItemBuilder(nextMat).name(plugin.getMessageManager().format(nextName, player));
            if (!nextLore.isEmpty()) btn.lore(nextLore.stream().map(l -> plugin.getMessageManager().format(l, player)).collect(Collectors.toList()));
            builder.set(nextSlot, btn.build());
        }

        // Back button (with optional skull texture)
        backSlot = bedrock ? 38 : cfg.getInt("navigation.back.slot", 47);
        String backName    = cfg.getString("navigation.back.name", "&7◁ Back");
        String backTexture = cfg.getString("navigation.back.skull-texture", "");
        Material backMat   = safeMaterial(cfg.getString("navigation.back.material"), Material.ARROW);
        ItemBuilder backBtn;
        if (backTexture != null && !backTexture.isBlank()) {
            backBtn = new ItemBuilder(Material.PLAYER_HEAD)
                .name(plugin.getMessageManager().format(backName, player))
                .skull(backTexture);
        } else {
            backBtn = new ItemBuilder(backMat)
                .name(plugin.getMessageManager().format(backName, player));
        }
        List<String> backLore = cfg.getStringList("navigation.back.lore");
        if (!backLore.isEmpty()) backBtn.lore(backLore.stream().map(l -> plugin.getMessageManager().format(l, player)).collect(Collectors.toList()));
        builder.set(backSlot, backBtn.build());

        // Search button (optional; disabled on Bedrock since we use row 4 for core nav)
        int searchSlot = bedrock ? -1 : cfg.getInt("navigation.search.slot", -1);
        if (searchSlot >= 0) {
            Material searchMat  = safeMaterial(cfg.getString("navigation.search.material"), Material.COMPASS);
            String   searchName = cfg.getString("navigation.search.name", "&e⚲ Search");
            builder.set(searchSlot, new ItemBuilder(searchMat)
                .name(plugin.getMessageManager().format(searchName, player))
                .build());
        }

        inventory = builder.build(new ShopHolder(shop, category));
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        // Back
        if (slot == backSlot) {
            plugin.getGuiManager().goBack(player);
            return;
        }

        // Prev page
        if (slot == prevSlot && clampedPage > 0) {
            plugin.getGuiManager().openReplace(player, new CategoryGui(plugin, shop, category, clampedPage - 1));
            return;
        }

        // Next page
        if (slot == nextSlot && clampedPage < totalPages - 1) {
            plugin.getGuiManager().openReplace(player, new CategoryGui(plugin, shop, category, clampedPage + 1));
            return;
        }

        // Item clicks
        for (int i = 0; i < currentItemSlots.length && i < pageItems.size(); i++) {
            if (slot != currentItemSlots[i]) continue;
            ShopItem item = pageItems.get(i);
            if (item.isDisplayOnly()) return;

            // Bedrock players (mobile/controller) have no reliable right-click.
            // Show an explicit Buy / Sell choice screen instead of left/right routing.
            if (bedrockPlayer) {
                plugin.getGuiManager().open(player, new BedrockItemChoiceGui(plugin, shop, category, item));
                return;
            }

            boolean leftClick  = event.isLeftClick();
            boolean rightClick = event.isRightClick();
            boolean shift      = event.isShiftClick();

            if (leftClick && !shift && item.isBuyable()) {
                plugin.getGuiManager().open(player, new BuyGui(plugin, shop, category, item));
            } else if (rightClick && item.isSellable()) {
                plugin.getGuiManager().open(player, new SellGui(plugin, shop, category, item));
            } else if (leftClick && shift && item.isBuyable()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    var txp = new dev.fluxshop.shop.TransactionProcessor(plugin);
                    var eco = plugin.getEconomyRegistry().get(item.getEconomyProvider());
                    if (eco == null) eco = plugin.getEconomyRegistry().getDefault();
                    if (eco == null) return;
                    double balance     = eco.getBalance(player.getUniqueId());
                    double pricePerOne = Math.max(0.01, priceCalc.getBuyPrice(item, player, 1));
                    int    max         = (int) Math.floor(balance / pricePerOne);
                    if (max > 0) txp.buy(player, item, max);
                    else plugin.getMessageManager().send(player, "transaction.buy-fail-funds",
                        "price",    eco.format(pricePerOne),
                        "balance",  eco.format(balance),
                        "currency", eco.getCurrencyName());
                });
            }
            return;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ItemStack buildItemIcon(ShopItem item, Player player) {
        if (item.getDisplayItem() == null) return new ItemStack(Material.BARRIER);

        var eco = plugin.getEconomyRegistry().get(item.getEconomyProvider());
        if (eco == null) eco = plugin.getEconomyRegistry().getDefault();

        boolean canBuy  = item.isBuyable()  && (item.getBuyPermission()  == null || player.hasPermission(item.getBuyPermission()));
        boolean canSell = item.isSellable() && (item.getSellPermission() == null || player.hasPermission(item.getSellPermission()));

        List<String> lore = new ArrayList<>();
        lore.add("");

        // Buy price line
        if (item.isBuyable()) {
            double buyPrice     = priceCalc.getBuyPrice(item, player, 1);
            String buyFormatted = eco != null
                ? eco.format(buyPrice) + " " + eco.getCurrencyName()
                : String.format("$%.2f", buyPrice);
            lore.add(plugin.getMessageManager().format("&7Buy: &a" + buyFormatted, player));
        } else {
            lore.add(plugin.getMessageManager().format("&7Buy: &cNot available", player));
        }

        // Sell price line
        if (item.isSellable()) {
            double sellPrice     = priceCalc.getSellPrice(item, player, 1);
            String sellFormatted = eco != null
                ? eco.format(sellPrice) + " " + eco.getCurrencyName()
                : String.format("$%.2f", sellPrice);
            lore.add(plugin.getMessageManager().format("&7Sell: &6" + sellFormatted, player));
        } else {
            lore.add(plugin.getMessageManager().format("&7Sell: &cNot available", player));
        }

        lore.add("");
        if (canBuy)  lore.add(plugin.getMessageManager().format("&eLeft-click: &fBuy", player));
        if (canSell) lore.add(plugin.getMessageManager().format("&eRight-click: &fSell", player));
        if (canBuy)  lore.add(plugin.getMessageManager().format("&eShift+click: &fBuy max", player));

        ItemBuilder builder = new ItemBuilder(item.getDisplayItem())
            .lore(lore)
            .glow(canBuy || canSell);

        if (item.getCustomName() != null) builder.name(item.getCustomName());
        return builder.build();
    }

    /** Replaces {page}, {total}, {items} placeholders in a nav label. */
    private String formatNav(String template, int currentPage, int total, int itemCount) {
        return template
            .replace("{page}",  String.valueOf(currentPage + 1))
            .replace("{total}", String.valueOf(total))
            .replace("{items}", String.valueOf(itemCount));
    }

    private List<String> formatNavLore(List<String> lines, int currentPage, int total, int itemCount) {
        return lines.stream()
            .map(l -> formatNav(l, currentPage, total, itemCount))
            .collect(Collectors.toList());
    }

    private static Material safeMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }
}
