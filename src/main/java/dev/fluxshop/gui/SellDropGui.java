package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.shop.SellAllProcessor;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SellGUI — Drag-and-drop sell interface.
 *
 * Layout (6 rows):
 *   Rows 0-3 (slots 0-35): drop zone — player places items to sell
 *   Row 4 (slots 36-44):   live total / info panel
 *   Row 5:                 SELL button (slot 49), CLEAR button (slot 47), BACK (slot 45)
 *
 * Items placed in the drop zone are previewed with their sell value.
 * Clicking SELL executes the batch sale and returns unsellable items.
 */
public class SellDropGui extends FluxGui {

    private static final int   ROWS       = 6;
    private static final int   TOTAL_SIZE = ROWS * 9;
    private static final int   DROP_ROWS  = 4;
    private static final int   DROP_SLOTS = DROP_ROWS * 9;  // 0-35

    private static final int   SELL_SLOT  = 49;
    private static final int   CLEAR_SLOT = 47;
    private static final int   BACK_SLOT  = 45;
    private static final int   INFO_SLOT  = 40;

    private final FluxShopPlugin plugin;
    private Inventory inventory;

    public SellDropGui(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void open(Player player) {
        String title = plugin.getConfigManager().getString("guis.sellgui.title", "&8Sell Items");
        title = plugin.getMessageManager().colorize(title);

        inventory = Bukkit.createInventory(new ShopHolder(null, null), TOTAL_SIZE, title);
        buildFrame();
        player.openInventory(inventory);
        plugin.getSoundManager().play(player, "shop-open");
    }

    private void buildFrame() {
        // Drop zone: fill with light grey pane as backdrop (slots 0-35)
        ItemStack dropMarker = new ItemBuilder(Material.WHITE_STAINED_GLASS_PANE)
            .name("&7Drop items here to sell")
            .build();
        for (int i = 0; i < DROP_SLOTS; i++) {
            inventory.setItem(i, dropMarker);
        }

        // Info row (slots 36-44): dark glass separators
        ItemStack sep = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 36; i <= 44; i++) {
            inventory.setItem(i, sep);
        }

        // Info panel
        updateInfoPanel(0.0, 0);

        // Bottom action row (45-53)
        ItemStack navSep = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i <= 53; i++) inventory.setItem(i, navSep);

        // Back button
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
            .name("&cGo Back")
            .build());

        // Clear button
        inventory.setItem(CLEAR_SLOT, new ItemBuilder(Material.BARRIER)
            .name("&cClear Items")
            .lore("&7Returns all items in the drop zone")
            .build());

        // Sell button
        inventory.setItem(SELL_SLOT, new ItemBuilder(Material.LIME_DYE)
            .name("&a&lSELL ALL")
            .lore("&7Click to sell all items in the drop zone")
            .glow()
            .build());
    }

    private void updateInfoPanel(double total, int count) {
        String ecoId = plugin.getConfigManager().getString("economy.default-provider", "vault");
        dev.fluxshop.economy.EconomyProvider eco = plugin.getEconomyRegistry().get(ecoId);
        String formatted = eco != null ? eco.format(total) : String.format("%.2f", total);

        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.GOLD_INGOT)
            .name("&6Total Value")
            .lore(
                "&7Items: &f" + count,
                "&7Value: &a" + formatted,
                "",
                "&eClick &aSELL ALL &eto receive payment"
            )
            .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // Allow player to place/remove items in drop zone (slots 0-35)
        if (slot >= 0 && slot < DROP_SLOTS) {
            // Un-cancel for drop zone so items can be placed
            event.setCancelled(false);
            // Defer recalculation to next tick after item is placed
            Bukkit.getScheduler().runTaskLater(plugin, () -> recalculate(player), 1L);
            return;
        }

        // Navigation / action buttons
        switch (slot) {
            case BACK_SLOT  -> { plugin.getGuiManager().goBack(player); }
            case CLEAR_SLOT -> { returnDropItems(player); buildFrame(); }
            case SELL_SLOT  -> { executeSell(player); }
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }

    // ── Internal helpers ────────────────────────────────────────────────────

    private void recalculate(Player player) {
        if (inventory == null) return;
        double total = 0.0;
        int count = 0;

        for (int i = 0; i < DROP_SLOTS; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) continue;
            // Skip the white pane marker
            if (stack.getType() == Material.WHITE_STAINED_GLASS_PANE) continue;

            List<ShopItem> matches = plugin.getShopManager().findSellableItems(stack);
            if (!matches.isEmpty()) {
                ShopItem best = matches.get(0);
                double price = plugin.getPriceCalculator().getSellPrice(best, player, stack.getAmount());
                total += price;
                count += stack.getAmount();
            }
        }
        updateInfoPanel(total, count);
    }

    /** Return any items still in the drop zone when the GUI is closed without selling. */
    @Override
    public void onClose(Player player) {
        returnDropItems(player);
    }

    private void returnDropItems(Player player) {
        List<ItemStack> toReturn = new ArrayList<>();
        for (int i = 0; i < DROP_SLOTS; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType() == Material.WHITE_STAINED_GLASS_PANE) continue;
            toReturn.add(item);
            inventory.setItem(i, null);
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(toReturn.toArray(new ItemStack[0]));
        leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private void executeSell(Player player) {
        // Collect items from drop zone
        List<ItemStack> dropItems = new ArrayList<>();
        for (int i = 0; i < DROP_SLOTS; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType() == Material.WHITE_STAINED_GLASS_PANE) continue;
            dropItems.add(item.clone());
            inventory.setItem(i, null);
        }

        if (dropItems.isEmpty()) {
            plugin.getMessageManager().send(player, "sellall.nothing-to-sell");
            return;
        }

        // Temporarily move items to player inventory for the sell processor
        // (SellAllProcessor reads from player.getInventory())
        // A simpler approach: process each item directly
        double totalEarned = 0.0;
        int    totalItems  = 0;
        List<ItemStack> unsellable = new ArrayList<>();

        for (ItemStack stack : dropItems) {
            List<ShopItem> matches = plugin.getShopManager().findSellableItems(stack);
            if (matches.isEmpty()) {
                unsellable.add(stack);
                continue;
            }
            ShopItem best = matches.get(0);
            if (best.getSellPrice() < 0) {
                unsellable.add(stack);
                continue;
            }

            double price = plugin.getPriceCalculator().getSellPrice(best, player, stack.getAmount());
            dev.fluxshop.economy.EconomyProvider ecoP =
                plugin.getEconomyRegistry().get(best.getEconomyProvider());
            if (ecoP == null) ecoP = plugin.getEconomyRegistry().getDefault();
            if (ecoP == null || !ecoP.deposit(player.getUniqueId(), price)) {
                unsellable.add(stack);
                plugin.getLogger().warning("[SellDropGui] Deposit of " + price + " failed for " + player.getName() + " — item returned.");
                continue;
            }
            totalEarned += price;
            totalItems  += stack.getAmount();
        }

        // Return unsellable items (drop on ground if inventory is full)
        if (!unsellable.isEmpty()) {
            Map<Integer, ItemStack> unsellableLeftover =
                player.getInventory().addItem(unsellable.toArray(new ItemStack[0]));
            unsellableLeftover.values().forEach(stack ->
                player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }

        // Feedback
        String ecoId = plugin.getConfigManager().getString("economy.default-provider", "vault");
        dev.fluxshop.economy.EconomyProvider eco2 = plugin.getEconomyRegistry().get(ecoId);
        String formatted = eco2 != null ? eco2.format(totalEarned) : String.format("%.2f", totalEarned);

        if (totalItems > 0) {
            plugin.getMessageManager().send(player, "sellall.success",
                "amount", formatted,
                "items", totalItems);
            plugin.getSoundManager().play(player, "sell-success");
            plugin.getParticleManager().spawn(player, "sell-success");
        } else {
            plugin.getMessageManager().send(player, "sellall.nothing-to-sell");
        }

        // Rebuild the empty frame
        buildFrame();
    }
}
