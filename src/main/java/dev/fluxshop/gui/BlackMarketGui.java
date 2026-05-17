package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.BlackMarketListing;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Black Market GUI — mysterious rotating shop.
 *
 * Layout (6 rows):
 *   Border: WITHER_SKELETON_SKULL (configured)
 *   Inner area (rows 1-4, slots 10-16, 19-25, 28-34, 37-43): listing slots
 *   Slot 49: countdown timer display
 *   Slot 45: Back
 */
public class BlackMarketGui extends FluxGui {

    // Inner item slots (5×7 minus corners already covered by border)
    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private static final int TIMER_SLOT = 49;
    private static final int BACK_SLOT  = 45;

    private final FluxShopPlugin           plugin;
    private       Inventory                inventory;
    private       List<BlackMarketListing> listings = new ArrayList<>();

    public BlackMarketGui(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void open(Player player) {
        String title = plugin.getMessageManager().colorize(
            plugin.getConfigManager().getGuiConfig("black_market").getString("title", "&0&lBlack Market"));
        inventory = Bukkit.createInventory(new ShopHolder(null, null), 54, title);

        listings = new ArrayList<>(plugin.getBlackMarketService().getCurrentListings());

        build(player);
        player.openInventory(inventory);
        plugin.getSoundManager().play(player, "blackmarket-open");
        plugin.getParticleManager().spawn(player, "blackmarket-enter");
    }

    private void build(Player player) {
        // ── Skull border ──────────────────────────────────────────────
        String borderMat = plugin.getConfigManager().getGuiConfig("black_market")
            .getString("border.material", "WITHER_SKELETON_SKULL");
        Material bMat = parseMaterial(borderMat, Material.GRAY_STAINED_GLASS_PANE);

        ItemStack borderItem = new ItemBuilder(bMat)
            .name("&8&l✦ Black Market ✦")
            .build();

        // Fill entire inventory first
        for (int i = 0; i < 54; i++) inventory.setItem(i, borderItem);

        // Clear inner area
        for (int slot : ITEM_SLOTS) inventory.setItem(slot, null);

        // ── Listings ──────────────────────────────────────────────────
        for (int i = 0; i < Math.min(listings.size(), ITEM_SLOTS.length); i++) {
            BlackMarketListing listing = listings.get(i);
            inventory.setItem(ITEM_SLOTS[i], buildListingIcon(listing, player));
        }

        // Fill empty listing slots
        ItemStack empty = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
            .name("&8No item")
            .build();
        for (int i = listings.size(); i < ITEM_SLOTS.length; i++) {
            inventory.setItem(ITEM_SLOTS[i], empty);
        }

        // ── Timer ──────────────────────────────────────────────────────
        java.time.Instant nextRotation = plugin.getBlackMarketService().getNextRotation();
        String timerLine;
        if (nextRotation != null) {
            long secsLeft = java.time.Instant.now().until(nextRotation, ChronoUnit.SECONDS);
            long hours = secsLeft / 3600;
            long mins  = (secsLeft % 3600) / 60;
            long secs  = secsLeft % 60;
            timerLine = "&7Refreshes in: &e" + hours + "h " + mins + "m " + secs + "s";
        } else {
            timerLine = "&7Refreshes on schedule.";
        }
        inventory.setItem(TIMER_SLOT, new ItemBuilder(Material.CLOCK)
            .name("&6&lNext Refresh")
            .lore(timerLine, "", "&7Check back for new rare items!")
            .build());

        // ── Back ──────────────────────────────────────────────────────
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW).name("&cBack").build());
    }

    private ItemStack buildListingIcon(BlackMarketListing listing, Player player) {
        if (listing.getItem() == null) return new ItemBuilder(Material.BARRIER).name("&cCorrupted listing").build();
        String ecoId = listing.getCurrency() != null ? listing.getCurrency() : "default";
        dev.fluxshop.economy.EconomyProvider eco = plugin.getEconomyRegistry().get(ecoId);
        String priceStr = eco != null ? eco.format(listing.getPrice()) : String.valueOf(listing.getPrice());

        int remaining = listing.getRemainingStock();

        return new ItemBuilder(listing.getItem().clone())
            .appendLore(
                "",
                "&8Black Market",
                "&7Price: &6" + priceStr,
                "&7Stock: " + (remaining > 0 ? "&a" + remaining : "&cSOLD OUT"),
                "",
                remaining > 0 ? "&eClick to purchase" : "&cOut of stock"
            )
            .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            plugin.getGuiManager().goBack(player);
            return;
        }

        // Check if the slot is a listing slot
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                if (i < listings.size()) {
                    handlePurchase(player, listings.get(i));
                }
                return;
            }
        }
    }

    private void handlePurchase(Player player, BlackMarketListing listing) {
        if (!listing.isInStock()) {
            plugin.getMessageManager().send(player, "black-market.out-of-stock");
            plugin.getSoundManager().play(player, "purchase-fail");
            return;
        }
        String err = plugin.getBlackMarketService().purchase(player, listing, 1);
        if (err != null) {
            plugin.getMessageManager().sendRaw(player, "&c" + err);
            plugin.getSoundManager().play(player, "purchase-fail");
        } else {
            plugin.getSoundManager().play(player, "purchase-success");
            plugin.getParticleManager().spawn(player, "purchase-success");
            // Refresh the GUI to show updated stock
            plugin.getGuiManager().openReplace(player, new BlackMarketGui(plugin));
        }
    }

    private Material parseMaterial(String name, Material fallback) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
