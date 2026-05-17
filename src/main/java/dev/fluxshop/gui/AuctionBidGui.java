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

/**
 * Auction Bid GUI — shown when a player clicks a listing.
 *
 * Layout (3 rows):
 *   Slot 13 — Item preview
 *   Slot 11 — Bid +min increment
 *   Slot 15 — Buy Now (if available)
 *   Slot 18 — Back
 *   Slot 26 — Cancel
 */
public class AuctionBidGui extends FluxGui {

    private static final int ITEM_SLOT    = 13;
    private static final int BID_SLOT     = 11;
    private static final int BUYNOW_SLOT  = 15;
    private static final int BACK_SLOT    = 18;
    private static final int CANCEL_SLOT  = 26;

    private final FluxShopPlugin  plugin;
    private final AuctionListing  listing;
    private       Inventory       inventory;

    public AuctionBidGui(FluxShopPlugin plugin, AuctionListing listing) {
        this.plugin  = plugin;
        this.listing = listing;
    }

    @Override
    public void open(Player player) {
        String title = plugin.getMessageManager().colorize("&5Place a Bid");
        inventory = Bukkit.createInventory(new ShopHolder(null, null), 27, title);
        build(player);
        player.openInventory(inventory);
    }

    private void build(Player player) {
        ItemStack border = new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) inventory.setItem(i, border);

        String ecoId = listing.getCurrency() != null ? listing.getCurrency() : "default";
        double currentBid = listing.getCurrentBid() > 0 ? listing.getCurrentBid() : listing.getStartPrice();
        double minIncrement = plugin.getConfigManager().getDouble("auction.bid-increment-percent", 5.0);
        double minNextBid = listing.getMinNextBid(minIncrement);
        dev.fluxshop.economy.EconomyProvider eco = plugin.getEconomyRegistry().get(ecoId);

        String currentBidStr = eco != null ? eco.format(currentBid) : String.valueOf(currentBid);
        String minBidStr     = eco != null ? eco.format(minNextBid)  : String.valueOf(minNextBid);

        long secondsLeft = Instant.now().until(listing.getExpiresAt(), ChronoUnit.SECONDS);

        // Item preview
        ItemStack previewItem = listing.getItem() != null
            ? listing.getItem().clone()
            : new ItemBuilder(Material.BARRIER).name("&cCorrupted listing").build();
        inventory.setItem(ITEM_SLOT, new ItemBuilder(previewItem)
            .appendLore(
                "",
                "&7Current Bid: &a" + currentBidStr,
                "&7Min Next Bid: &6" + minBidStr,
                "&7Expires in: &e" + (secondsLeft / 3600) + "h " + ((secondsLeft % 3600) / 60) + "m",
                "&7Seller: &f" + listing.getSellerName()
            )
            .build());

        // Bid button
        inventory.setItem(BID_SLOT, new ItemBuilder(Material.GOLD_INGOT)
            .name("&6Place Bid")
            .lore("&7Minimum bid: &a" + minBidStr,
                  "&eClick to bid &a" + minBidStr)
            .glow()
            .build());

        // Buy Now
        if (listing.hasBuyNow()) {
            String buyNowStr = eco != null ? eco.format(listing.getBuyNowPrice()) : String.valueOf(listing.getBuyNowPrice());
            inventory.setItem(BUYNOW_SLOT, new ItemBuilder(Material.EMERALD)
                .name("&aBuy Now")
                .lore("&7Price: &a" + buyNowStr,
                      "&eClick to purchase instantly")
                .glow()
                .build());
        }

        // Back
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW).name("&cBack").build());
        inventory.setItem(CANCEL_SLOT, new ItemBuilder(Material.BARRIER).name("&cClose").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        double minIncrement = plugin.getConfigManager().getDouble("auction.bid-increment-percent", 5.0);
        double minNextBid   = listing.getMinNextBid(minIncrement);

        switch (slot) {
            case BID_SLOT -> {
                String err = plugin.getAuctionService().placeBid(player, listing, minNextBid);
                if (err != null) {
                    plugin.getMessageManager().sendRaw(player, "&c" + err);
                    plugin.getSoundManager().play(player, "purchase-fail");
                } else {
                    String item = listing.getItem() != null ? listing.getItem().getType().name() : "Item";
                    String eco  = listing.getCurrency();
                    dev.fluxshop.economy.EconomyProvider ep = plugin.getEconomyRegistry().get(eco);
                    plugin.getMessageManager().send(player, "auction.bid-placed",
                        "item", item,
                        "bid", ep != null ? ep.format(minNextBid) : String.format("%.2f", minNextBid),
                        "currency", ep != null ? ep.getCurrencyName() : "");
                    plugin.getSoundManager().play(player, "auction-bid");
                    plugin.getGuiManager().goBack(player);
                }
            }
            case BUYNOW_SLOT -> {
                if (listing.hasBuyNow()) {
                    String err = plugin.getAuctionService().buyNow(player, listing);
                    if (err != null) {
                        plugin.getMessageManager().sendRaw(player, "&c" + err);
                        plugin.getSoundManager().play(player, "purchase-fail");
                    } else {
                        // AuctionService.completeSale() already sends "auction.won" and plays
                        // "auction-win" to online players — do not duplicate those here.
                        player.closeInventory();
                    }
                }
            }
            case BACK_SLOT, CANCEL_SLOT -> plugin.getGuiManager().goBack(player);
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
