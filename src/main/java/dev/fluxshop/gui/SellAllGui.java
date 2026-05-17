package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.economy.EconomyProvider;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.shop.PriceCalculator;
import dev.fluxshop.shop.SellAllProcessor;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Sell-All preview GUI — shows each sellable item type from the player's
 * inventory with its unit sell price and total earnings, then lets the player
 * confirm or cancel before the actual sale is executed.
 *
 * Layout (6 rows):
 *   Slots 0-44  — preview of sellable items (one slot per material type)
 *   Slot 45     — Cancel / Back
 *   Slot 49     — Total earnings display
 *   Slot 53     — Confirm: Sell All
 */
public class SellAllGui extends FluxGui {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int CANCEL_SLOT    = 45;
    private static final int TOTAL_SLOT     = 49;
    private static final int CONFIRM_SLOT   = 53;

    private final FluxShopPlugin plugin;
    private       Inventory      inventory;

    /** Per-material breakdown: material → {shopItem, totalQty, totalEarnings}. */
    private final List<SellEntry> entries = new ArrayList<>();
    private double grandTotal = 0;

    public SellAllGui(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    @Override
    public void open(Player player) {
        String title = plugin.getMessageManager().colorize(
            plugin.getConfigManager().getString("guis.sell_screen.sellall_title", "&6Sell All — Preview"));
        inventory = Bukkit.createInventory(new ShopHolder(null, null), 54, title);

        buildEntries(player);
        build(player);
        player.openInventory(inventory);
    }

    /** Scans the player's inventory and groups sellable items by material. */
    private void buildEntries(Player player) {
        entries.clear();
        grandTotal = 0;
        PriceCalculator calc = new PriceCalculator(plugin);

        // Tally quantities per material
        Map<Material, Integer> qtys = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            qtys.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }

        for (Map.Entry<Material, Integer> e : qtys.entrySet()) {
            List<ShopItem> matches = plugin.getShopManager().findSellableItems(new ItemStack(e.getKey()));
            if (matches.isEmpty()) continue;

            // Best sell price across all shops
            ShopItem best = matches.stream()
                .max(Comparator.comparingDouble(i -> calc.getSellPrice(i, player, 1)))
                .orElse(matches.get(0));

            double unitPrice = calc.getSellPrice(best, player, 1);
            if (unitPrice <= 0) continue;

            double earnings = calc.getSellPrice(best, player, e.getValue());
            entries.add(new SellEntry(e.getKey(), best, e.getValue(), earnings));
            grandTotal += earnings;
        }
    }

    private void build(Player player) {
        // Border row
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) inventory.setItem(i, border);
        for (int i = 0; i < ITEMS_PER_PAGE; i++) inventory.setItem(i, null);

        // Item preview slots
        EconomyProvider eco = plugin.getEconomyRegistry().getDefault();
        for (int i = 0; i < Math.min(entries.size(), ITEMS_PER_PAGE); i++) {
            SellEntry entry = entries.get(i);
            String priceStr = eco != null ? eco.format(entry.earnings) : String.format("%.2f", entry.earnings);
            String unitStr  = eco != null ? eco.format(entry.earnings / entry.qty)
                                          : String.format("%.2f", entry.earnings / entry.qty);

            inventory.setItem(i, new ItemBuilder(entry.material)
                .name("&f" + prettyName(entry.material))
                .lore(
                    "&7Quantity: &f" + entry.qty,
                    "&7Unit sell: &a" + unitStr,
                    "&7Total: &6" + priceStr
                )
                .build());
        }

        // Empty filler for unused preview slots
        if (entries.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                .name("&cNothing to sell")
                .lore("&7None of your items have a sell value in any shop.")
                .build());
        }

        // Cancel
        inventory.setItem(CANCEL_SLOT, new ItemBuilder(Material.ARROW).name("&cCancel").build());

        // Total display
        String totalStr = eco != null ? eco.format(grandTotal) : String.format("%.2f", grandTotal);
        inventory.setItem(TOTAL_SLOT, new ItemBuilder(Material.GOLD_INGOT)
            .name("&6&lTotal Earnings")
            .lore(
                "&7Items: &f" + entries.size() + " type(s)",
                "&7You will earn: &6" + totalStr,
                "",
                entries.isEmpty() ? "&cNothing to sell" : "&7Click &aConfirm &7to sell everything."
            )
            .build());

        // Confirm
        if (!entries.isEmpty()) {
            inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_WOOL)
                .name("&a&lConfirm: Sell All")
                .lore("&7Sell all sellable items for &6" + totalStr)
                .glow()
                .build());
        } else {
            inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.RED_WOOL)
                .name("&c&lNothing to Sell")
                .lore("&7Your inventory has no sellable items.")
                .build());
        }
    }

    // ── Click ─────────────────────────────────────────────────────────────────

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == CANCEL_SLOT) {
            plugin.getGuiManager().goBack(player);
        } else if (slot == CONFIRM_SLOT && !entries.isEmpty()) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () ->
                new SellAllProcessor(plugin).sellAll(player, null));
        }
        // Item preview slots are info-only — no action
    }

    @Override
    public Inventory getInventory() { return inventory; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String prettyName(Material mat) {
        String raw = mat.name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)))
              .append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private record SellEntry(Material material, ShopItem shopItem, int qty, double earnings) {}
}
