package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.economy.EconomyProvider;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Auction listing creation GUI.
 *
 * Layout (4 rows, 36 slots):
 *   Slot 13 — item to list (player places it here from hotbar)
 *   Slot 11 — Start price  (click → chat input)
 *   Slot 15 — Buy-now price (click → chat input; click again to disable)
 *   Slot 29 — Duration selector (cycles 6h → 12h → 24h → 48h)
 *   Slot 31 — Confirm / List
 *   Slot 33 — Cancel (returns placed item)
 */
public class AuctionCreateGui extends FluxGui {

    private static final int ITEM_SLOT     = 13;
    private static final int START_SLOT    = 11;
    private static final int BUYNOW_SLOT   = 15;
    private static final int DURATION_SLOT = 29;
    private static final int CONFIRM_SLOT  = 31;
    private static final int CANCEL_SLOT   = 33;

    private final FluxShopPlugin plugin;
    private Inventory inventory;

    private double startPrice  = 100;
    private double buyNowPrice = -1;   // -1 = disabled
    private int    durationH   = 24;

    /** Pre-set item from player's hand (may be null). */
    private final ItemStack presetItem;

    /** Which field is awaiting chat input: "start", "buynow", or null. */
    private String awaitingInput = null;
    private Listener chatListener;

    public AuctionCreateGui(FluxShopPlugin plugin) {
        this(plugin, null);
    }

    public AuctionCreateGui(FluxShopPlugin plugin, ItemStack presetItem) {
        this.plugin     = plugin;
        this.presetItem = presetItem;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    @Override
    public void open(Player player) {
        String title = plugin.getMessageManager().colorize(
            plugin.getConfigManager().getString("guis.auction_house.create_title", "&5List Item for Auction"));
        if (inventory == null) {
            inventory = Bukkit.createInventory(new ShopHolder(null, null), 36, title);
            if (presetItem != null) inventory.setItem(ITEM_SLOT, presetItem.clone());
        }
        build();
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() { return inventory; }

    @Override
    public void onClose(Player player) {
        unregisterChat();
    }

    // ── Build layout ──────────────────────────────────────────────────────────

    private void build() {
        // Snapshot the placed item BEFORE the border fill overwrites slot 13
        ItemStack savedItem = inventory.getItem(ITEM_SLOT);
        boolean hasRealItem = savedItem != null
            && savedItem.getType() != Material.AIR
            && savedItem.getType() != Material.PURPLE_STAINED_GLASS_PANE
            && savedItem.getType() != Material.ITEM_FRAME;

        ItemStack border = new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 36; i++) inventory.setItem(i, border);

        // Restore the player's item, or show the empty-slot placeholder
        if (hasRealItem) {
            inventory.setItem(ITEM_SLOT, savedItem);
        } else {
            inventory.setItem(ITEM_SLOT, new ItemBuilder(Material.ITEM_FRAME)
                .name("&eItem to Sell")
                .lore("&7Click with an item, or", "&7shift-click from your inventory")
                .build());
        }

        EconomyProvider eco = plugin.getEconomyRegistry().getDefault();
        String symbol = eco != null ? eco.getCurrencySymbol() : "$";

        // Start price
        inventory.setItem(START_SLOT, new ItemBuilder(Material.GOLD_NUGGET)
            .name("&6Start Price")
            .lore("&7Current: &a" + symbol + String.format("%.2f", startPrice),
                  "&eClick to change")
            .build());

        // Buy-now
        inventory.setItem(BUYNOW_SLOT, new ItemBuilder(buyNowPrice > 0 ? Material.EMERALD : Material.COAL)
            .name("&aBuy-Now Price")
            .lore("&7Current: " + (buyNowPrice > 0 ? "&a" + symbol + String.format("%.2f", buyNowPrice) : "&cDisabled"),
                  buyNowPrice > 0 ? "&eClick to change or &cdisable" : "&eClick to enable")
            .build());

        // Duration
        inventory.setItem(DURATION_SLOT, new ItemBuilder(Material.CLOCK)
            .name("&eDuration")
            .lore("&7Duration: &f" + durationH + "h",
                  "&eClick to cycle: &76h &7/ &f12h &7/ &f24h &7/ &f48h")
            .build());

        // Confirm
        inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_WOOL)
            .name("&a&lLIST ITEM")
            .lore("&7Start: &6" + symbol + String.format("%.2f", startPrice),
                  "&7Buy-now: " + (buyNowPrice > 0 ? "&a" + symbol + String.format("%.2f", buyNowPrice) : "&cDisabled"),
                  "&7Duration: &f" + durationH + "h",
                  "",
                  "&aClick to list!")
            .glow()
            .build());

        // Cancel
        inventory.setItem(CANCEL_SLOT, new ItemBuilder(Material.RED_WOOL)
            .name("&c&lCANCEL")
            .lore("&7Returns any placed item")
            .build());
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // Item slot — allow clicks so player can place/swap items
        if (slot == ITEM_SLOT) {
            event.setCancelled(false);
            Bukkit.getScheduler().runTaskLater(plugin, this::build, 1L);
            return;
        }

        switch (slot) {
            case START_SLOT -> {
                startChatInput(player, "start");
            }
            case BUYNOW_SLOT -> {
                if (buyNowPrice > 0) {
                    buyNowPrice = -1; // disable
                    build();
                } else {
                    startChatInput(player, "buynow");
                }
            }
            case DURATION_SLOT -> {
                durationH = switch (durationH) { case 6 -> 12; case 12 -> 24; case 24 -> 48; default -> 6; };
                build();
            }
            case CONFIRM_SLOT -> handleConfirm(player);
            case CANCEL_SLOT  -> handleCancel(player);
        }
    }

    // ── Chat input ────────────────────────────────────────────────────────────

    private void startChatInput(Player player, String field) {
        player.closeInventory();
        awaitingInput = field;
        if (field.equals("start")) {
            plugin.getMessageManager().sendRaw(player, "&5[Auction] &7Type the starting bid price &8(or &ccancel&8):");
        } else {
            plugin.getMessageManager().sendRaw(player, "&5[Auction] &7Type the buy-now price &8(or &ccancel &8to disable):");
        }

        chatListener = new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onChat(AsyncPlayerChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                event.setCancelled(true);
                String msg = event.getMessage().trim();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    unregisterChat();
                    awaitingInput = null;

                    if (msg.equalsIgnoreCase("cancel")) {
                        if (field.equals("buynow")) buyNowPrice = -1;
                        plugin.getMessageManager().sendRaw(player, "&7[Auction] Input cancelled.");
                    } else {
                        try {
                            double val = Double.parseDouble(msg);
                            if (val < 0) val = 0;
                            if (field.equals("start")) {
                                startPrice = val;
                                // Reset buy-now if it's now below start
                                if (buyNowPrice > 0 && buyNowPrice < startPrice) buyNowPrice = startPrice * 2;
                            } else {
                                if (val <= startPrice) {
                                    plugin.getMessageManager().sendRaw(player, "&c[Auction] Buy-now must be higher than start price (" + startPrice + ").");
                                    buyNowPrice = -1;
                                } else {
                                    buyNowPrice = val;
                                }
                            }
                        } catch (NumberFormatException e) {
                            plugin.getMessageManager().sendRaw(player, "&c[Auction] Invalid number. Value unchanged.");
                        }
                    }
                    if (player.isOnline()) plugin.getGuiManager().openReplace(player, AuctionCreateGui.this);
                });
            }
        };
        Bukkit.getPluginManager().registerEvents(chatListener, plugin);

        // 30-second timeout
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (chatListener != null) {
                unregisterChat();
                awaitingInput = null;
                plugin.getMessageManager().sendRaw(player, "&c[Auction] Input timed out.");
                if (player.isOnline()) plugin.getGuiManager().openReplace(player, AuctionCreateGui.this);
            }
        }, 600L);
    }

    // ── Confirm / Cancel ──────────────────────────────────────────────────────

    private void handleConfirm(Player player) {
        ItemStack listed = inventory.getItem(ITEM_SLOT);
        if (listed == null || listed.getType() == Material.AIR || listed.getType() == Material.ITEM_FRAME) {
            plugin.getMessageManager().sendRaw(player, "&cPlace an item in the listing slot first!");
            return;
        }
        if (startPrice <= 0) {
            plugin.getMessageManager().sendRaw(player, "&cStart price must be greater than zero.");
            return;
        }

        String currency = plugin.getEconomyRegistry().getDefault() != null
            ? plugin.getEconomyRegistry().getDefault().getId() : "vault";

        player.closeInventory();
        plugin.getAuctionService().listItem(player, listed, startPrice, buyNowPrice, durationH, currency)
            .thenAccept(err -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null) {
                    plugin.getMessageManager().sendRaw(player, "&c[Auction] " + err);
                } else {
                    plugin.getMessageManager().send(player, "auction.listed-success", "hours", String.valueOf(durationH));
                    plugin.getSoundManager().play(player, "auction-list");
                    plugin.getGuiManager().open(player, new AuctionHouseGui(plugin, 0));
                }
            }));
    }

    private void handleCancel(Player player) {
        // Return any item placed in the listing slot
        ItemStack placed = inventory.getItem(ITEM_SLOT);
        if (placed != null && placed.getType() != Material.AIR && placed.getType() != Material.ITEM_FRAME) {
            player.getInventory().addItem(placed);
            inventory.setItem(ITEM_SLOT, null);
        }
        plugin.getGuiManager().goBack(player);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void unregisterChat() {
        if (chatListener != null) {
            HandlerList.unregisterAll(chatListener);
            chatListener = null;
        }
    }
}
