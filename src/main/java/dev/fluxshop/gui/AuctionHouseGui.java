package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.AuctionListing;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Auction House browser GUI.
 *
 * Layout (6 rows):
 *   Slots 0-44  → listing slots (5 rows × 9 = 45 items per page)
 *   Slot 45     → Previous page
 *   Slot 46     → Filter / Search (chat input)
 *   Slot 47     → Back
 *   Slot 48     → My Listings
 *   Slot 49     → Collect winnings
 *   Slot 50     → Sell / List new item
 *   Slot 52     → Sort toggle (price asc/desc/newest)
 *   Slot 53     → Next page
 *
 * Sort modes:   NEWEST → PRICE_ASC → PRICE_DESC → NEWEST ...
 * Search:       chat input, case-insensitive match on item display name or type name.
 */
public class AuctionHouseGui extends FluxGui {

    private enum SortMode { NEWEST, PRICE_ASC, PRICE_DESC }

    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_SLOT      = 45;
    private static final int FILTER_SLOT    = 46;
    private static final int BACK_SLOT      = 47;
    private static final int MY_LISTINGS    = 48;
    private static final int COLLECT_SLOT   = 49;
    private static final int LIST_SLOT      = 50;
    private static final int SORT_SLOT      = 52;
    private static final int NEXT_SLOT      = 53;

    private final FluxShopPlugin plugin;
    private final int            page;
    private final SortMode       sortMode;
    private final String         filter;   // null = no filter

    private Inventory            inventory;
    private List<AuctionListing> listings = new ArrayList<>();
    /** Total listing count before pagination — used for accurate next-page detection. */
    private int                  totalListingCount = 0;

    private Listener chatListener;

    // ── Constructors ──────────────────────────────────────────────────────────

    public AuctionHouseGui(FluxShopPlugin plugin, int page) {
        this(plugin, page, SortMode.NEWEST, null);
    }

    private AuctionHouseGui(FluxShopPlugin plugin, int page, SortMode sort, String filter) {
        this.plugin   = plugin;
        this.page     = Math.max(0, page);
        this.sortMode = sort;
        this.filter   = filter;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    @Override
    public void open(Player player) {
        String baseTitle = plugin.getConfigManager().getString("guis.auction_house.title", "&5Auction House");
        String pageLabel = " &8[" + (filter != null ? "Search: " + filter + " | " : "") + "Page " + (page + 1) + "]";
        String title = plugin.getMessageManager().colorize(baseTitle + pageLabel);

        inventory = Bukkit.createInventory(new ShopHolder(null, null), 54, title);
        loadListings();
        build(player);
        player.openInventory(inventory);
        plugin.getSoundManager().play(player, "auction-bid");
    }

    @Override
    public Inventory getInventory() { return inventory; }

    @Override
    public void onClose(Player player) {
        unregisterChat();
    }

    // ── Load & sort ───────────────────────────────────────────────────────────

    private void loadListings() {
        List<AuctionListing> all = new ArrayList<>(plugin.getAuctionService().getActiveListings());

        // Apply text filter
        if (filter != null && !filter.isBlank()) {
            String lower = filter.toLowerCase();
            all = all.stream().filter(l -> {
                ItemStack item = l.getItem();
                if (item == null) return false;
                // Match against display name or material type name
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()
                        && meta.getDisplayName().toLowerCase().contains(lower)) return true;
                return item.getType().name().toLowerCase().contains(lower);
            }).collect(Collectors.toList());
        }

        // Apply sort
        Comparator<AuctionListing> comp = switch (sortMode) {
            case PRICE_ASC  -> Comparator.comparingDouble(l ->
                l.getCurrentBid() > 0 ? l.getCurrentBid() : l.getStartPrice());
            case PRICE_DESC -> Comparator.comparingDouble((AuctionListing l) ->
                l.getCurrentBid() > 0 ? l.getCurrentBid() : l.getStartPrice()).reversed();
            default         -> Comparator.comparing(AuctionListing::getCreatedAt).reversed();
        };
        all.sort(comp);

        // Save total before paginating so next-page button is accurate
        totalListingCount = all.size();

        // Paginate
        int from = page * ITEMS_PER_PAGE;
        int to   = Math.min(from + ITEMS_PER_PAGE, all.size());
        listings  = from < all.size() ? new ArrayList<>(all.subList(from, to)) : new ArrayList<>();
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void build(Player player) {
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) inventory.setItem(i, border);
        for (int i = 0;  i < ITEMS_PER_PAGE; i++) inventory.setItem(i, null);

        // Listings
        for (int i = 0; i < Math.min(listings.size(), ITEMS_PER_PAGE); i++) {
            inventory.setItem(i, buildListingIcon(listings.get(i), player));
        }
        // Empty slot filler
        ItemStack empty = new ItemBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE).name("&7No listing").build();
        for (int i = listings.size(); i < ITEMS_PER_PAGE; i++) inventory.setItem(i, empty);

        // Navigation
        inventory.setItem(PREV_SLOT, page > 0
            ? new ItemBuilder(Material.ARROW).name("&ePrevious Page").build()
            : new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        boolean hasNextPage = totalListingCount > (long)(page + 1) * ITEMS_PER_PAGE;
        inventory.setItem(NEXT_SLOT, hasNextPage
            ? new ItemBuilder(Material.ARROW).name("&eNext Page").build()
            : new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        // Filter / Search
        inventory.setItem(FILTER_SLOT, new ItemBuilder(Material.HOPPER)
            .name("&eSearch / Filter")
            .lore(filter != null ? "&7Active filter: &f" + filter : "&7No filter active",
                  "&eClick to search by item name or type",
                  filter != null ? "&cRight-click to clear filter" : "")
            .build());

        // Sort
        String sortLabel = switch (sortMode) {
            case PRICE_ASC  -> "&eSort: Price ↑";
            case PRICE_DESC -> "&eSort: Price ↓";
            default         -> "&eSort: Newest";
        };
        inventory.setItem(SORT_SLOT, new ItemBuilder(Material.COMPARATOR)
            .name(sortLabel)
            .lore("&7Click to cycle sort order",
                  "&8Newest → Price ↑ → Price ↓")
            .build());

        inventory.setItem(MY_LISTINGS, new ItemBuilder(Material.PAPER)
            .name("&eYour Listings")
            .lore("&7View your active auction listings")
            .build());
        inventory.setItem(COLLECT_SLOT, new ItemBuilder(Material.CHEST)
            .name("&aCollect Items")
            .lore("&7Claim won auctions or expired listings",
                  plugin.getAuctionService().hasItemsToCollect(player.getUniqueId())
                      ? "&a● Items waiting!" : "&7Nothing to collect right now")
            .glow(plugin.getAuctionService().hasItemsToCollect(player.getUniqueId()))
            .build());
        inventory.setItem(LIST_SLOT, new ItemBuilder(Material.EMERALD)
            .name("&aSell an Item")
            .lore("&7Click to list a new item for auction")
            .glow()
            .build());
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW).name("&cBack").build());
    }

    private ItemStack buildListingIcon(AuctionListing listing, Player player) {
        if (listing.getItem() == null) return new ItemBuilder(Material.BARRIER).name("&cCorrupted listing").build();
        ItemStack base = listing.getItem().clone();
        long secs  = Instant.now().until(listing.getExpiresAt(), ChronoUnit.SECONDS);
        long hours = Math.max(0, secs / 3600);
        long mins  = Math.max(0, (secs % 3600) / 60);

        String ecoId = listing.getCurrency() != null ? listing.getCurrency() : "default";
        var eco = plugin.getEconomyRegistry().get(ecoId);
        double bidVal = listing.getCurrentBid() > 0 ? listing.getCurrentBid() : listing.getStartPrice();
        String bidStr = eco != null ? eco.format(bidVal) : String.format("%.2f", bidVal);
        String buyNow = listing.hasBuyNow()
            ? (eco != null ? eco.format(listing.getBuyNowPrice()) : String.format("%.2f", listing.getBuyNowPrice()))
            : "N/A";
        String sellerName = listing.getSellerName() != null
            ? listing.getSellerName() : listing.getSellerUuid().toString();

        boolean ownListing = listing.getSellerUuid().equals(player.getUniqueId());

        return new ItemBuilder(base)
            .appendLore(
                "",
                "&8Auction House",
                "&7Seller: &f" + sellerName + (ownListing ? " &7(You)" : ""),
                listing.hasBid() ? "&7Top bid: &a" + bidStr : "&7Start price: &a" + bidStr,
                "&7Buy Now: &6" + buyNow,
                "&7Expires: &e" + hours + "h " + mins + "m",
                "",
                ownListing ? "&cYour listing" : "&eLeft-click &7to bid",
                (listing.hasBuyNow() && !ownListing) ? "&aRight-click &7to buy now" : ""
            )
            .build();
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // Listing slots
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            if (slot < listings.size()) {
                AuctionListing listing = listings.get(slot);
                if (listing.getSellerUuid().equals(player.getUniqueId())) {
                    plugin.getMessageManager().send(player, "auction.cant-bid-own");
                    plugin.getSoundManager().play(player, "purchase-fail");
                    return;
                }
                if (event.getClick() == ClickType.RIGHT && listing.hasBuyNow()) {
                    handleBuyNow(player, listing);
                } else {
                    plugin.getGuiManager().open(player, new AuctionBidGui(plugin, listing));
                }
            }
            return;
        }

        switch (slot) {
            case PREV_SLOT -> {
                if (page > 0)
                    plugin.getGuiManager().openReplace(player,
                        new AuctionHouseGui(plugin, page - 1, sortMode, filter));
            }
            case NEXT_SLOT -> {
                if (totalListingCount > (long)(page + 1) * ITEMS_PER_PAGE)
                    plugin.getGuiManager().openReplace(player,
                        new AuctionHouseGui(plugin, page + 1, sortMode, filter));
            }
            case FILTER_SLOT -> {
                if (event.getClick() == ClickType.RIGHT && filter != null) {
                    // Clear filter
                    plugin.getGuiManager().openReplace(player,
                        new AuctionHouseGui(plugin, 0, sortMode, null));
                } else {
                    startSearchInput(player);
                }
            }
            case SORT_SLOT -> {
                SortMode next = switch (sortMode) {
                    case NEWEST    -> SortMode.PRICE_ASC;
                    case PRICE_ASC -> SortMode.PRICE_DESC;
                    default        -> SortMode.NEWEST;
                };
                plugin.getGuiManager().openReplace(player,
                    new AuctionHouseGui(plugin, 0, next, filter));
            }
            case MY_LISTINGS  -> plugin.getGuiManager().open(player, new MyListingsGui(plugin, player));
            case COLLECT_SLOT -> { player.closeInventory(); plugin.getAuctionService().collect(player); }
            case LIST_SLOT    -> plugin.getGuiManager().open(player, new AuctionCreateGui(plugin));
            case BACK_SLOT    -> plugin.getGuiManager().goBack(player);
        }
    }

    // ── Buy-now ───────────────────────────────────────────────────────────────

    private void handleBuyNow(Player player, AuctionListing listing) {
        String err = plugin.getAuctionService().buyNow(player, listing);
        if (err != null) {
            plugin.getMessageManager().sendRaw(player, "&c" + err);
            plugin.getSoundManager().play(player, "purchase-fail");
        } else {
            // AuctionService.completeSale() already sends "auction.won" and plays "auction-win"
            // to online players — do not duplicate those here.
            player.closeInventory();
        }
    }

    // ── Search chat input ─────────────────────────────────────────────────────

    private void startSearchInput(Player player) {
        player.closeInventory();
        plugin.getMessageManager().sendRaw(player, "&5[Auction] &7Type the item name to search for &8(or &ccancel&8):");

        chatListener = new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onChat(AsyncPlayerChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                event.setCancelled(true);
                String msg = event.getMessage().trim();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    unregisterChat();
                    if (msg.equalsIgnoreCase("cancel")) {
                        if (player.isOnline())
                            plugin.getGuiManager().openReplace(player,
                                new AuctionHouseGui(plugin, 0, sortMode, filter));
                        return;
                    }
                    if (player.isOnline())
                        plugin.getGuiManager().openReplace(player,
                            new AuctionHouseGui(plugin, 0, sortMode, msg));
                });
            }
        };
        Bukkit.getPluginManager().registerEvents(chatListener, plugin);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (chatListener != null) {
                unregisterChat();
                plugin.getMessageManager().sendRaw(player, "&c[Auction] Search input timed out.");
                if (player.isOnline())
                    plugin.getGuiManager().openReplace(player,
                        new AuctionHouseGui(plugin, 0, sortMode, filter));
            }
        }, 600L);
    }

    private void unregisterChat() {
        if (chatListener != null) {
            HandlerList.unregisterAll(chatListener);
            chatListener = null;
        }
    }
}
