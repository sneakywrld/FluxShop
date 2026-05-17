package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.TradeOffer;
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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player Trade GUI — split-pane interface.
 *
 * Layout (6 rows, 54 slots):
 *   Left  side (cols 0-3): Player A item slots
 *   Center col 4:          Divider
 *   Right side (cols 5-8): Player B item slots
 *
 *   Bottom row:
 *     Slot 45 — Player A confirm
 *     Slot 46 — Player A currency offer (click to enter amount via chat)
 *     Slots 47-51 — bottom border
 *     Slot 52 — Player B currency offer
 *     Slot 53 — Player B confirm
 *
 * Anti-scam: any item or currency change resets both confirmations.
 * Both players see the same shared Inventory instance so changes are live.
 */
public class TradeGui extends FluxGui {

    // Player A item slots: col 0-3, rows 0-4
    private static final int[] SLOTS_A = { 0,1,2,3, 9,10,11,12, 18,19,20,21, 27,28,29,30, 36,37,38,39 };
    // Player B item slots: col 5-8, rows 0-4
    private static final int[] SLOTS_B = { 5,6,7,8, 14,15,16,17, 23,24,25,26, 32,33,34,35, 41,42,43,44 };
    // Divider col 4
    private static final int[] DIVIDER  = { 4,13,22,31,40 };

    private static final int CONFIRM_A  = 45;
    private static final int CURRENCY_A = 46;
    private static final int CONFIRM_B  = 53;
    private static final int CURRENCY_B = 52;

    private final FluxShopPlugin plugin;
    private final TradeOffer     offer;
    private final UUID           playerA;
    private final UUID           playerB;
    private       Inventory      inventory;

    /** Players currently waiting to type a currency amount in chat. */
    private final Set<UUID> awaitingCurrencyInput = ConcurrentHashMap.newKeySet();

    /** Chat listener registered while at least one player is in currency-input mode. */
    private Listener chatListener;

    public TradeGui(FluxShopPlugin plugin, TradeOffer offer) {
        this.plugin  = plugin;
        this.offer   = offer;
        this.playerA = offer.getPlayerA();
        this.playerB = offer.getPlayerB();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void open(Player player) {
        if (inventory == null) {
            String title = plugin.getMessageManager().colorize(
                plugin.getConfigManager().getString("guis.trade.title", "&8Player Trade"));
            inventory = Bukkit.createInventory(new ShopHolder(null, null), 54, title);
            buildLayout();
        }
        player.openInventory(inventory);
        plugin.getSoundManager().play(player, "shop-open");
    }

    @Override
    public Inventory getInventory() { return inventory; }

    @Override
    public void onClose(Player player) {
        awaitingCurrencyInput.remove(player.getUniqueId());
        unregisterChatListenerIfEmpty();
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    /** Full rebuild — used on open and after any state change. */
    private void buildLayout() {
        ItemStack div = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s : DIVIDER) inventory.setItem(s, div);

        ItemStack bottomBorder = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) inventory.setItem(i, bottomBorder);

        refreshItemSlots();
        refreshCurrencyButtons();
        refreshConfirmButtons();
    }

    private void refreshItemSlots() {
        Player pA = Bukkit.getPlayer(playerA);
        Player pB = Bukkit.getPlayer(playerB);
        String nameA = pA != null ? pA.getName() : offer.getPlayerAName();
        String nameB = pB != null ? pB.getName() : offer.getPlayerBName();

        for (int i = 0; i < SLOTS_A.length; i++) {
            ItemStack item = offer.getItemsA().size() > i ? offer.getItemsA().get(i) : null;
            inventory.setItem(SLOTS_A[i], item != null ? item : emptySlot(nameA));
        }
        for (int i = 0; i < SLOTS_B.length; i++) {
            ItemStack item = offer.getItemsB().size() > i ? offer.getItemsB().get(i) : null;
            inventory.setItem(SLOTS_B[i], item != null ? item : emptySlot(nameB));
        }
    }

    private void refreshCurrencyButtons() {
        Player pA = Bukkit.getPlayer(playerA);
        Player pB = Bukkit.getPlayer(playerB);
        String nameA = pA != null ? pA.getName() : offer.getPlayerAName();
        String nameB = pB != null ? pB.getName() : offer.getPlayerBName();

        var eco = plugin.getEconomyRegistry().get(offer.getCurrency());
        String symbol = eco != null ? eco.getCurrencySymbol() : "$";

        inventory.setItem(CURRENCY_A, new ItemBuilder(Material.GOLD_NUGGET)
            .name("&6" + nameA + "'s Coin Offer")
            .lore(
                "&7Amount: &a" + symbol + String.format("%.2f", offer.getCurrencyAmountA()),
                "",
                "&eClick to change the amount"
            ).build());

        inventory.setItem(CURRENCY_B, new ItemBuilder(Material.GOLD_NUGGET)
            .name("&6" + nameB + "'s Coin Offer")
            .lore(
                "&7Amount: &a" + symbol + String.format("%.2f", offer.getCurrencyAmountB()),
                "",
                "&eClick to change the amount"
            ).build());
    }

    private void refreshConfirmButtons() {
        Player pA = Bukkit.getPlayer(playerA);
        Player pB = Bukkit.getPlayer(playerB);
        String nameA = pA != null ? pA.getName() : offer.getPlayerAName();
        String nameB = pB != null ? pB.getName() : offer.getPlayerBName();

        boolean ca = offer.isConfirmedByA();
        boolean cb = offer.isConfirmedByB();

        inventory.setItem(CONFIRM_A, new ItemBuilder(ca ? Material.LIME_WOOL : Material.RED_WOOL)
            .name(ca ? "&a&l✔ " + nameA + " is Ready!" : "&c" + nameA + " — Click to Confirm")
            .lore(ca ? "&7Waiting for the other player..." : "&7Both players must confirm to execute the trade.")
            .glow(ca).build());

        inventory.setItem(CONFIRM_B, new ItemBuilder(cb ? Material.LIME_WOOL : Material.RED_WOOL)
            .name(cb ? "&a&l✔ " + nameB + " is Ready!" : "&c" + nameB + " — Click to Confirm")
            .lore(cb ? "&7Waiting for the other player..." : "&7Both players must confirm to execute the trade.")
            .glow(cb).build());
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        boolean isA = player.getUniqueId().equals(playerA);

        // Allow placing/removing items on the player's own side only
        int[] mySlots = isA ? SLOTS_A : SLOTS_B;
        for (int s : mySlots) {
            if (s == slot) {
                event.setCancelled(false); // allow item movement
                // Sync offer state on next tick (after Bukkit resolves the click)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    syncItemsFromGui(isA);
                }, 1L);
                return;
            }
        }

        // Prevent clicking the other player's item slots
        int[] theirSlots = isA ? SLOTS_B : SLOTS_A;
        for (int s : theirSlots) {
            if (s == slot) return; // cancelled by GuiListener already
        }

        // Currency button
        if ((isA && slot == CURRENCY_A) || (!isA && slot == CURRENCY_B)) {
            startCurrencyInput(player, isA);
            return;
        }

        // Confirm button — only own confirm
        if ((isA && slot == CONFIRM_A) || (!isA && slot == CONFIRM_B)) {
            handleConfirm(player, isA);
        }
    }

    // ── Item sync ─────────────────────────────────────────────────────────────

    private void syncItemsFromGui(boolean isA) {
        if (isA) {
            offer.getItemsA().clear();
            for (int s : SLOTS_A) {
                ItemStack item = inventory.getItem(s);
                if (item != null && !item.getType().isAir()
                        && item.getType() != Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                    offer.getItemsA().add(item.clone());
                }
            }
        } else {
            offer.getItemsB().clear();
            for (int s : SLOTS_B) {
                ItemStack item = inventory.getItem(s);
                if (item != null && !item.getType().isAir()
                        && item.getType() != Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                    offer.getItemsB().add(item.clone());
                }
            }
        }
        // Any item change resets confirmations (anti-scam)
        offer.resetConfirmations();
        refreshConfirmButtons();
        refreshItemSlots(); // re-draw empty slots
    }

    // ── Currency chat input ───────────────────────────────────────────────────

    private void startCurrencyInput(Player player, boolean isA) {
        player.closeInventory();
        plugin.getMessageManager().sendRaw(player, "&6[Trade] &7Type the coin amount to offer &8(or &ccancel&8):");

        awaitingCurrencyInput.add(player.getUniqueId());
        ensureChatListenerRegistered();

        // Timeout after 30s — re-open GUI regardless
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (awaitingCurrencyInput.remove(player.getUniqueId())) {
                plugin.getMessageManager().sendRaw(player, "&c[Trade] &7Currency input timed out.");
                unregisterChatListenerIfEmpty();
                if (player.isOnline()) plugin.getGuiManager().openReplace(player, this);
            }
        }, 600L); // 30 seconds
    }

    private void ensureChatListenerRegistered() {
        if (chatListener != null) return;
        TradeGui self = this;
        chatListener = new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onChat(AsyncPlayerChatEvent event) {
                Player p = event.getPlayer();
                if (!awaitingCurrencyInput.contains(p.getUniqueId())) return;

                event.setCancelled(true); // consume the message

                String msg = event.getMessage().trim();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!awaitingCurrencyInput.remove(p.getUniqueId())) return;
                    unregisterChatListenerIfEmpty();

                    if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("0")) {
                        plugin.getMessageManager().sendRaw(p, "&7[Trade] Currency offer cancelled (set to 0).");
                        boolean isA = p.getUniqueId().equals(playerA);
                        if (isA) offer.setCurrencyAmountA(0);
                        else     offer.setCurrencyAmountB(0);
                        // Any currency change — including cancel/0 — must reset confirmations
                        // to prevent a scammer from changing the amount after the other player confirmed.
                        offer.resetConfirmations();
                        refreshConfirmButtons();
                        refreshCurrencyButtons();
                    } else {
                        try {
                            double amount = Double.parseDouble(msg);
                            if (amount < 0) {
                                plugin.getMessageManager().sendRaw(p, "&c[Trade] Amount must be positive.");
                                amount = 0;
                            }
                            boolean isA = p.getUniqueId().equals(playerA);
                            if (isA) offer.setCurrencyAmountA(amount);
                            else     offer.setCurrencyAmountB(amount);
                            offer.resetConfirmations();
                            refreshConfirmButtons();
                            refreshCurrencyButtons();
                            plugin.getMessageManager().sendRaw(p, "&a[Trade] Currency offer updated.");
                        } catch (NumberFormatException e) {
                            plugin.getMessageManager().sendRaw(p, "&c[Trade] Invalid amount. Currency offer unchanged.");
                        }
                    }
                    if (p.isOnline()) plugin.getGuiManager().openReplace(p, self);
                });
            }
        };
        Bukkit.getPluginManager().registerEvents(chatListener, plugin);
    }

    private void unregisterChatListenerIfEmpty() {
        if (awaitingCurrencyInput.isEmpty() && chatListener != null) {
            HandlerList.unregisterAll(chatListener);
            chatListener = null;
        }
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    private void handleConfirm(Player player, boolean isA) {
        plugin.getTradeManager().confirm(player);
        if (offer.getStatus() != TradeOffer.Status.BOTH_CONFIRMED
                && offer.getStatus() != TradeOffer.Status.COMPLETE
                && offer.getStatus() != TradeOffer.Status.CANCELLED) {
            refreshConfirmButtons();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack emptySlot(String ownerName) {
        return new ItemBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
            .name("&7" + ownerName + "'s slot").build();
    }
}
