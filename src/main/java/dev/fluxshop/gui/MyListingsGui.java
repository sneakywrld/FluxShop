package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.AuctionListing;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shows the current player's own active auction listings.
 *
 * Layout (6 rows):
 *   Slots 0-44 — own listings
 *   Slot 45    — Back
 *   Slot 49    — Collect
 *   Slot 53    — Close
 */
public class MyListingsGui extends FluxGui {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int BACK_SLOT      = 45;
    private static final int COLLECT_SLOT   = 49;
    private static final int CLOSE_SLOT     = 53;

    private final FluxShopPlugin plugin;
    private final UUID           ownerUuid;
    private       Inventory      inventory;
    private       List<AuctionListing> listings;

    public MyListingsGui(FluxShopPlugin plugin, Player player) {
        this.plugin    = plugin;
        this.ownerUuid = player.getUniqueId();
    }

    @Override
    public void open(Player player) {
        String title = plugin.getMessageManager().colorize("&5Your Listings");
        inventory = Bukkit.createInventory(new ShopHolder(null, null), 54, title);

        listings = new ArrayList<>(plugin.getAuctionService().getActiveListings().stream()
            .filter(l -> l.getSellerUuid().equals(ownerUuid))
            .toList());

        build(player);
        player.openInventory(inventory);
    }

    private void build(Player player) {
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) inventory.setItem(i, border);
        for (int i = 0; i < ITEMS_PER_PAGE; i++) inventory.setItem(i, null);

        if (listings.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                .name("&cNo Active Listings")
                .lore("&7You have no items listed for auction.", "&7Use &f/auction list &7to create one.")
                .build());
        }

        for (int i = 0; i < Math.min(listings.size(), ITEMS_PER_PAGE); i++) {
            inventory.setItem(i, buildIcon(listings.get(i)));
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW).name("&cBack").build());
        inventory.setItem(COLLECT_SLOT, new ItemBuilder(Material.CHEST)
            .name("&aCollect Items")
            .lore("&7Claim expired listings or won auctions")
            .build());
        inventory.setItem(CLOSE_SLOT, new ItemBuilder(Material.BARRIER).name("&cClose").build());
    }

    private ItemStack buildIcon(AuctionListing listing) {
        long secs  = Instant.now().until(listing.getExpiresAt(), ChronoUnit.SECONDS);
        long hours = Math.max(0, secs / 3600);
        long mins  = Math.max(0, (secs % 3600) / 60);

        var eco = plugin.getEconomyRegistry().get(listing.getCurrency());
        double bid = listing.getCurrentBid() > 0 ? listing.getCurrentBid() : listing.getStartPrice();
        String bidStr = eco != null ? eco.format(bid) : String.format("%.2f", bid);

        if (listing.getItem() == null) return new ItemBuilder(Material.BARRIER).name("&cCorrupted listing").build();
        return new ItemBuilder(listing.getItem().clone())
            .appendLore(
                "",
                "&8Your listing",
                listing.hasBid() ? "&7Top bid: &a" + bidStr : "&7Start price: &a" + bidStr,
                listing.hasBuyNow() ? "&7Buy-now: &6" + (eco != null
                    ? eco.format(listing.getBuyNowPrice())
                    : String.format("%.2f", listing.getBuyNowPrice())) : "",
                "&7Expires: &e" + hours + "h " + mins + "m",
                "",
                "&cLeft-click &7to cancel and reclaim"
            )
            .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot >= 0 && slot < ITEMS_PER_PAGE && slot < listings.size()) {
            AuctionListing listing = listings.get(slot);
            String err = plugin.getAuctionService().cancelListing(player, listing.getId());
            if (err != null) {
                plugin.getMessageManager().sendRaw(player, "&c" + err);
                plugin.getSoundManager().play(player, "purchase-fail");
            } else {
                player.closeInventory();
                plugin.getAuctionService().collect(player); // claim the returned item immediately
            }
            return;
        }

        switch (slot) {
            case BACK_SLOT    -> plugin.getGuiManager().goBack(player);
            case COLLECT_SLOT -> { player.closeInventory(); plugin.getAuctionService().collect(player); }
            case CLOSE_SLOT   -> player.closeInventory();
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
