package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.economy.EconomyProvider;
import dev.fluxshop.model.Shop;
import dev.fluxshop.model.ShopCategory;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.shop.PriceCalculator;
import dev.fluxshop.shop.TransactionProcessor;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Buy transaction quantity-selection screen.
 *
 * Quantity options: x1, x8, x16, x32, x64, Max (can afford), Custom (chat input).
 * Selecting Custom closes the GUI, prompts the player to type a number in chat,
 * then re-opens the GUI with the chosen quantity pre-selected.
 */
public class BuyGui extends FluxGui {

    private final FluxShopPlugin  plugin;
    private final Shop            shop;
    private final ShopCategory    category;
    private final ShopItem        item;
    private final PriceCalculator priceCalc;

    // Quantity button config
    private static final int[] QTY_SLOTS   = {18, 19, 20, 21, 22, 23, 24};
    private static final int[] QTY_AMOUNTS = { 1,  8, 16, 32, 64, -1, -2};
    // -1 = max affordable, -2 = custom (chat input)

    private int selectedQty = 1;

    /** Chat listener registered while the player is typing a custom quantity. */
    private Listener chatListener;

    public BuyGui(FluxShopPlugin plugin, Shop shop, ShopCategory category, ShopItem item) {
        this.plugin    = plugin;
        this.shop      = shop;
        this.category  = category;
        this.item      = item;
        this.priceCalc = new PriceCalculator(plugin);
    }

    /** Internal constructor for re-opening with a pre-set quantity. */
    private BuyGui(FluxShopPlugin plugin, Shop shop, ShopCategory category, ShopItem item, int presetQty) {
        this(plugin, shop, category, item);
        this.selectedQty = presetQty;
    }

    // ── Open / build ──────────────────────────────────────────────────────────

    @Override
    public void open(Player player) {
        EconomyProvider eco = resolveEco();
        if (eco == null) {
            plugin.getMessageManager().sendRaw(player, "&cNo economy plugin is available.");
            return;
        }
        double balance       = eco.getBalance(player.getUniqueId());
        double pricePerOne   = Math.max(0.01, priceCalc.getBuyPrice(item, player, 1));
        int    maxAffordable = (int) Math.floor(balance / pricePerOne);
        if (selectedQty == -1) selectedQty = maxAffordable;
        if (selectedQty < 1)   selectedQty = 1;

        double totalPrice = priceCalc.getBuyPrice(item, player, selectedQty);

        FileConfiguration cfg  = plugin.getConfigManager().getGuiConfig("buy_screen");
        String itemDisplayName = item.getCustomName() != null ? item.getCustomName() : "Item";
        String titleTemplate   = cfg.getString("title", "<green><bold>Buy {item}");
        String title = plugin.getMessageManager().format(
            titleTemplate.replace("{item}", itemDisplayName), player);

        int      rows      = cfg.getInt("rows", 4);
        Material borderMat = safeMaterial(cfg.getString("border.material"),     Material.GREEN_STAINED_GLASS_PANE);
        Material bgMat     = safeMaterial(cfg.getString("background.material"), Material.GRAY_STAINED_GLASS_PANE);

        GuiBuilder builder = new GuiBuilder(rows, title)
            .border(borderMat, " ")
            .fill(bgMat, " ");

        // Item preview (slot 13)
        builder.set(13, item.getDisplayItem().clone());

        // Price info (slot 11)
        builder.set(11, buildPriceInfo(eco, totalPrice, balance, player));

        // Quantity buttons
        Material[] qtyMaterials = {
            Material.WHITE_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE
        };
        String[] qtyLabels = {
            "&fx1", "&bx8", "&bx16", "&9x32", "&5x64",
            "&aMax (&f" + maxAffordable + "&a)",
            "&eCustom"
        };

        for (int i = 0; i < QTY_SLOTS.length; i++) {
            int qty = QTY_AMOUNTS[i] == -1 ? maxAffordable : QTY_AMOUNTS[i];
            boolean selected = (QTY_AMOUNTS[i] != -2 && qty == selectedQty);
            builder.set(QTY_SLOTS[i], new ItemBuilder(
                selected ? Material.LIME_STAINED_GLASS_PANE : qtyMaterials[i])
                .name(qtyLabels[i])
                .glow(selected)
                .build());
        }

        // Confirm (slot 15)
        builder.set(15, new ItemBuilder(Material.LIME_CONCRETE)
            .name("&a&l✔ Confirm Purchase")
            .lore("&7Buy &fx" + selectedQty + " &7for &6" + eco.format(totalPrice) + " " + eco.getCurrencyName())
            .build());

        // Cancel (slot 17)
        builder.set(17, new ItemBuilder(Material.RED_CONCRETE)
            .name("&c&l✘ Cancel")
            .lore("&7Go back without buying.")
            .build());

        // Back (slot 31)
        builder.set(31, new ItemBuilder(Material.ARROW).name("&7◁ Back").build());

        inventory = builder.build(new ShopHolder(shop, category));
        player.openInventory(inventory);
    }

    @Override
    public void onClose(Player player) {
        if (chatListener != null) {
            HandlerList.unregisterAll(chatListener);
            chatListener = null;
        }
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        // Back / Cancel
        if (slot == 17 || slot == 31) { plugin.getGuiManager().goBack(player); return; }

        // Confirm purchase
        if (slot == 15) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> executeBuy(player));
            return;
        }

        // Quantity selection
        for (int i = 0; i < QTY_SLOTS.length; i++) {
            if (slot != QTY_SLOTS[i]) continue;

            if (QTY_AMOUNTS[i] == -2) {
                // Custom quantity — chat input
                startCustomQtyInput(player);
                return;
            }

            EconomyProvider eco = resolveEco();
            int maxAffordable = 0;
            if (eco != null) {
                double balance     = eco.getBalance(player.getUniqueId());
                double pricePerOne = Math.max(0.01, priceCalc.getBuyPrice(item, player, 1));
                maxAffordable      = (int) Math.floor(balance / pricePerOne);
            }

            selectedQty = QTY_AMOUNTS[i] == -1 ? maxAffordable : QTY_AMOUNTS[i];
            if (selectedQty < 1) selectedQty = 1;
            plugin.getGuiManager().openReplace(player, new BuyGui(plugin, shop, category, item, selectedQty));
            return;
        }
    }

    // ── Custom quantity chat input ─────────────────────────────────────────────

    private void startCustomQtyInput(Player player) {
        player.closeInventory();
        plugin.getMessageManager().sendRaw(player, "&6[Shop] &7Type the quantity you want to buy &8(or &ccancel&8):");

        chatListener = new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onChat(AsyncPlayerChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                event.setCancelled(true);
                String msg = event.getMessage().trim();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Guard against race where onClose() already ran before this task executed
                    if (chatListener == null) return;
                    HandlerList.unregisterAll(chatListener);
                    chatListener = null;

                    if (msg.equalsIgnoreCase("cancel")) {
                        plugin.getMessageManager().sendRaw(player, "&7[Shop] Quantity input cancelled.");
                        if (player.isOnline()) {
                            BuyGui gui = new BuyGui(plugin, shop, category, item, selectedQty);
                            plugin.getGuiManager().openReplace(player, gui);
                        }
                        return;
                    }
                    try {
                        int qty = Integer.parseInt(msg);
                        if (qty < 1) {
                            plugin.getMessageManager().sendRaw(player, "&c[Shop] Quantity must be at least 1.");
                            qty = 1;
                        }
                        if (player.isOnline()) {
                            plugin.getGuiManager().openReplace(player,
                                new BuyGui(plugin, shop, category, item, qty));
                        }
                    } catch (NumberFormatException e) {
                        plugin.getMessageManager().sendRaw(player, "&c[Shop] Invalid number. Quantity unchanged.");
                        if (player.isOnline()) {
                            plugin.getGuiManager().openReplace(player,
                                new BuyGui(plugin, shop, category, item, selectedQty));
                        }
                    }
                });
            }
        };
        Bukkit.getPluginManager().registerEvents(chatListener, plugin);

        // Timeout after 30 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (chatListener != null) {
                HandlerList.unregisterAll(chatListener);
                chatListener = null;
                plugin.getMessageManager().sendRaw(player, "&c[Shop] Quantity input timed out.");
                if (player.isOnline()) {
                    plugin.getGuiManager().openReplace(player,
                        new BuyGui(plugin, shop, category, item, selectedQty));
                }
            }
        }, 600L);
    }

    // ── Purchase execution ────────────────────────────────────────────────────

    private void executeBuy(Player player) {
        EconomyProvider eco = resolveEco();
        TransactionProcessor.Result result = new TransactionProcessor(plugin).buy(player, item, selectedQty);

        if (result == TransactionProcessor.Result.NO_FUNDS && eco != null) {
            plugin.getMessageManager().send(player, "transaction.buy-fail-funds",
                "price",    eco.format(priceCalc.getBuyPrice(item, player, selectedQty)),
                "balance",  eco.format(eco.getBalance(player.getUniqueId())),
                "currency", eco.getCurrencyName());
        }
        // Other failure messages are sent inside TransactionProcessor itself
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private EconomyProvider resolveEco() {
        EconomyProvider eco = plugin.getEconomyRegistry().get(item.getEconomyProvider());
        if (eco == null) eco = plugin.getEconomyRegistry().getDefault();
        return eco;
    }

    private static Material safeMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }

    private ItemStack buildPriceInfo(EconomyProvider eco, double totalPrice, double balance, Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Buy price: &a" + eco.format(priceCalc.getBuyPrice(item, player, 1)) + " " + eco.getCurrencyName());
        lore.add("&7Quantity:  &fx" + selectedQty);
        lore.add("&7Total:     &6" + eco.format(totalPrice) + " " + eco.getCurrencyName());
        lore.add("");
        lore.add("&7Balance: &a" + eco.format(balance) + " " + eco.getCurrencyName());
        lore.add("&7After purchase: &a"
            + eco.format(Math.max(0, balance - totalPrice)) + " " + eco.getCurrencyName());
        return new ItemBuilder(Material.SUNFLOWER).name("&6&lPrice Info").lore(lore).build();
    }
}
