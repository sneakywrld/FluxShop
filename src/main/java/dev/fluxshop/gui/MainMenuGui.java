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

import java.util.List;
import java.util.stream.Collectors;

/**
 * The main shop menu — shows all available category icons.
 * Layout, border, background, and button config are all driven by guis/main_menu.yml.
 */
public class MainMenuGui extends FluxGui {

    private final FluxShopPlugin plugin;
    private final Shop           shop;

    // Static fallback slots (used if layout.available-slots is missing from config)
    private static final int[] DEFAULT_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    };

    /** Populated from config on every open() call, used by handleClick(). */
    private int[] activeSlots = DEFAULT_SLOTS;
    /** Close button slot — kept in sync with what was rendered so handleClick() matches. */
    private int activeCloseSlot = 49;

    public MainMenuGui(FluxShopPlugin plugin, Shop shop) {
        this.plugin = plugin;
        this.shop   = shop;
    }

    // Bedrock (GeyserMC): 5-row version uses rows 1–3 for categories
    private static final int[] BEDROCK_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34
    };

    @Override
    public void open(Player player) {
        FileConfiguration cfg = plugin.getConfigManager().getGuiConfig("main_menu");

        String title = plugin.getMessageManager().format(
            cfg.getString("title", "<bold><gradient:#FFD700:#FFA500>✦ FluxShop ✦</gradient></bold>"),
            player
        );

        boolean bedrock = plugin.getCompatManager().isBedrockPlayer(player);

        // Bedrock players get a 5-row GUI; Java players use config value (default 6)
        int      rows      = bedrock ? 5 : cfg.getInt("rows", 6);
        Material borderMat = safeMaterial(cfg.getString("border.material"),     Material.BLACK_STAINED_GLASS_PANE);
        Material bgMat     = safeMaterial(cfg.getString("background.material"), Material.GRAY_STAINED_GLASS_PANE);

        // Refresh available slots from config (Bedrock always uses the 5-row layout)
        if (bedrock) {
            activeSlots = BEDROCK_SLOTS;
        } else {
            List<Integer> configSlots = cfg.getIntegerList("layout.available-slots");
            if (!configSlots.isEmpty()) {
                activeSlots = configSlots.stream().mapToInt(i -> i).toArray();
            }
        }

        GuiBuilder builder = new GuiBuilder(rows, title)
            .border(borderMat, " ")
            .fill(bgMat, " ");

        // Bottom divider row — row 4 on Bedrock (slots 36-44), row 5 on Java (from config)
        String   divMaterialStr = cfg.getString("decorations.divider.material");
        Material divMat         = safeMaterial(divMaterialStr, borderMat);
        if (bedrock) {
            for (int s = 36; s <= 44; s++) builder.set(s, ItemBuilder.pane(divMat, " "));
        } else {
            for (int s : cfg.getIntegerList("decorations.divider.slots")) {
                builder.set(s, ItemBuilder.pane(divMat, " "));
            }
        }

        // Close button — slot 40 on Bedrock (row 4 centre), config slot on Java
        activeCloseSlot     = bedrock ? 40 : cfg.getInt("decorations.close.slot", 49);
        Material closeMat   = safeMaterial(cfg.getString("decorations.close.material"), Material.BARRIER);
        String   closeName  = cfg.getString("decorations.close.name", "<red><bold>✕ Close");
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

        // Place category icons
        List<ShopCategory> categories = shop.getCategories();
        for (int i = 0; i < categories.size() && i < activeSlots.length; i++) {
            ShopCategory cat = categories.get(i);
            if (!cat.isEnabled() || cat.isHidden()) continue;
            int slot = cat.getIconSlot() >= 0 ? cat.getIconSlot() : activeSlots[i];
            if (cat.getPermission() != null && !player.hasPermission(cat.getPermission())) {
                builder.set(slot, buildLockedIcon(cat));
                continue;
            }
            builder.set(slot, buildCategoryIcon(cat, player));
        }

        inventory = builder.build(new ShopHolder(shop, null));
        player.openInventory(inventory);
        plugin.getSoundManager().play(player, "shop-open");
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        // Close button
        if (slot == activeCloseSlot) {
            player.closeInventory();
            return;
        }

        // Category click
        List<ShopCategory> categories = shop.getCategories();
        for (int i = 0; i < categories.size() && i < activeSlots.length; i++) {
            ShopCategory cat    = categories.get(i);
            int          catSlot = cat.getIconSlot() >= 0 ? cat.getIconSlot() : activeSlots[i];
            if (slot == catSlot && cat.isEnabled() && !cat.isHidden()) {
                if (cat.getPermission() != null && !player.hasPermission(cat.getPermission())) {
                    plugin.getMessageManager().send(player, "shop.no-access");
                    plugin.getSoundManager().play(player, "purchase-fail");
                    return;
                }
                plugin.getGuiManager().open(player, new CategoryGui(plugin, shop, cat, 0));
                return;
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ItemStack buildCategoryIcon(ShopCategory cat, Player player) {
        ItemBuilder builder = new ItemBuilder(cat.getIcon() != null ? cat.getIcon() : new ItemStack(Material.CHEST));
        String name = plugin.getMessageManager().format(
            "<gradient:#FFD700:#FFA500><bold>" + cat.getDisplayName() + "</bold></gradient>", player);
        builder.name(name);

        long buyCount  = cat.getItems().stream().filter(ShopItem::isBuyable).count();
        long sellCount = cat.getItems().stream().filter(ShopItem::isSellable).count();
        builder.lore(List.of(
            plugin.getMessageManager().format("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━", player),
            plugin.getMessageManager().format("<gray>" + cat.getItems().size() + " items available", player),
            plugin.getMessageManager().format("<dark_gray>Buy: <green>" + buyCount
                + " <dark_gray>• Sell: <gold>" + sellCount, player),
            "",
            plugin.getMessageManager().format("<yellow>▶ Left-click to browse", player)
        ));
        return builder.build();
    }

    private ItemStack buildLockedIcon(ShopCategory cat) {
        return new ItemBuilder(Material.BARRIER)
            .name(plugin.getMessageManager().format("<red><bold>✕ " + cat.getDisplayName()))
            .lore(
                plugin.getMessageManager().format("<gray>You don't have permission"),
                plugin.getMessageManager().format("<gray>to access this category.")
            )
            .build();
    }

    private static Material safeMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }
}
