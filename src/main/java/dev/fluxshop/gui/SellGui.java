package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.economy.EconomyProvider;
import org.bukkit.configuration.file.FileConfiguration;
import dev.fluxshop.model.Shop;
import dev.fluxshop.model.ShopCategory;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.shop.PriceCalculator;
import dev.fluxshop.shop.TransactionProcessor;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;

import java.util.ArrayList;
import java.util.List;

/**
 * Sell transaction quantity-selection screen (mirrors BuyGui).
 *
 * Quantity options: x1, x8, x16, x32, x64, All (player has), Custom (chat input).
 */
public class SellGui extends FluxGui {

    private static final int[] QTY_SLOTS   = {18, 19, 20, 21, 22, 23, 24};
    private static final int[] QTY_AMOUNTS = { 1,  8, 16, 32, 64, -1, -2};
    // -1 = all player has, -2 = custom (chat input)

    private final FluxShopPlugin  plugin;
    private final Shop            shop;
    private final ShopCategory    category;
    private final ShopItem        item;
    private final PriceCalculator priceCalc;
    private int selectedQty = 1;

    /** Chat listener registered while the player is typing a custom quantity. */
    private Listener chatListener;

    public SellGui(FluxShopPlugin plugin, Shop shop, ShopCategory category, ShopItem item) {
        this.plugin    = plugin;
        this.shop      = shop;
        this.category  = category;
        this.item      = item;
        this.priceCalc = new PriceCalculator(plugin);
    }

    private SellGui(FluxShopPlugin plugin, Shop shop, ShopCategory category, ShopItem item, int presetQty) {
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
        int playerHas = countItems(player, item.getDisplayItem());
        if (selectedQty == -1) selectedQty = playerHas;
        if (selectedQty < 1)   selectedQty = 1;
        double totalPrice = priceCalc.getSellPrice(item, player, selectedQty);

        FileConfiguration cfg  = plugin.getConfigManager().getGuiConfig("sell_screen");
        String itemDisplayName = item.getCustomName() != null ? item.getCustomName() : "Item";
        String titleTemplate   = cfg.getString("title", "&6&lSell {item}");
        String title = plugin.getMessageManager().format(
            titleTemplate.replace("{item}", itemDisplayName), player);

        int      rows      = cfg.getInt("rows", 4);
        Material borderMat = safeMaterial(cfg.getString("border.material"),     Material.YELLOW_STAINED_GLASS_PANE);
        Material bgMat     = safeMaterial(cfg.getString("background.material"), Material.GRAY_STAINED_GLASS_PANE);

        GuiBuilder builder = new GuiBuilder(rows, title)
            .border(borderMat, " ")
            .fill(bgMat, " ");

        builder.set(13, item.getDisplayItem().clone());
        builder.set(11, buildPriceInfo(eco, totalPrice, playerHas, player));

        String[] qtyLabels = {
            "&fx1", "&ex8", "&6x16", "&6x32", "&cx64",
            "&aAll (&f" + playerHas + "&a)",
            "&eCustom"
        };
        Material[] qtyMats = {
            Material.WHITE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.WRITABLE_BOOK
        };

        for (int i = 0; i < QTY_SLOTS.length; i++) {
            int qty = QTY_AMOUNTS[i] == -1 ? playerHas : QTY_AMOUNTS[i];
            boolean sel = (QTY_AMOUNTS[i] != -2 && qty == selectedQty);
            builder.set(QTY_SLOTS[i], new ItemBuilder(sel ? Material.LIME_STAINED_GLASS_PANE : qtyMats[i])
                .name(qtyLabels[i]).glow(sel).build());
        }

        builder.set(15, new ItemBuilder(Material.GOLD_BLOCK)
            .name("&6&l✔ Confirm Sale")
            .lore("&7Sell &fx" + selectedQty + " &7for &6" + eco.format(totalPrice) + " " + eco.getCurrencyName())
            .build());

        builder.set(17, new ItemBuilder(Material.RED_CONCRETE)
            .name("&c&l✘ Cancel").lore("&7Go back without selling.").build());
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

        if (slot == 17 || slot == 31) { plugin.getGuiManager().goBack(player); return; }

        if (slot == 15) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () ->
                new TransactionProcessor(plugin).sell(player, item, selectedQty));
            return;
        }

        for (int i = 0; i < QTY_SLOTS.length; i++) {
            if (slot != QTY_SLOTS[i]) continue;

            if (QTY_AMOUNTS[i] == -2) {
                startCustomQtyInput(player);
                return;
            }

            int playerHas = countItems(player, item.getDisplayItem());
            selectedQty   = QTY_AMOUNTS[i] == -1 ? playerHas : QTY_AMOUNTS[i];
            selectedQty   = Math.max(1, Math.min(selectedQty, playerHas));
            plugin.getGuiManager().openReplace(player, new SellGui(plugin, shop, category, item, selectedQty));
            return;
        }
    }

    // ── Custom quantity chat input ─────────────────────────────────────────────

    private void startCustomQtyInput(Player player) {
        player.closeInventory();
        int playerHas = countItems(player, item.getDisplayItem());
        plugin.getMessageManager().sendRaw(player, "&6[Shop] &7You have &f" + playerHas + " &7of this item. Type how many to sell &8(or &ccancel&8):");

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
                        if (player.isOnline())
                            plugin.getGuiManager().openReplace(player,
                                new SellGui(plugin, shop, category, item, selectedQty));
                        return;
                    }
                    try {
                        int qty = Integer.parseInt(msg);
                        int has = countItems(player, item.getDisplayItem());
                        if (qty < 1) qty = 1;
                        if (qty > has) {
                            plugin.getMessageManager().sendRaw(player, "&c[Shop] You only have &f" + has + " &cof that item.");
                            qty = has;
                        }
                        if (player.isOnline())
                            plugin.getGuiManager().openReplace(player,
                                new SellGui(plugin, shop, category, item, qty));
                    } catch (NumberFormatException e) {
                        plugin.getMessageManager().sendRaw(player, "&c[Shop] Invalid number. Quantity unchanged.");
                        if (player.isOnline())
                            plugin.getGuiManager().openReplace(player,
                                new SellGui(plugin, shop, category, item, selectedQty));
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
                if (player.isOnline())
                    plugin.getGuiManager().openReplace(player,
                        new SellGui(plugin, shop, category, item, selectedQty));
            }
        }, 600L);
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

    @SuppressWarnings("deprecation")
    private int countItems(Player player, ItemStack template) {
        int count = 0;
        ItemMeta tMeta = template.getItemMeta();
        for (ItemStack i : player.getInventory().getContents()) {
            if (i == null || i.getType() != template.getType()) continue;
            // Potion-type and enchanted-book aware matching
            ItemMeta cMeta = i.getItemMeta();
            if (tMeta instanceof PotionMeta tpm && cMeta instanceof PotionMeta cpm) {
                PotionData td = tpm.getBasePotionData();
                PotionData cd = cpm.getBasePotionData();
                if (td != null && (cd == null || td.getType() != cd.getType())) continue;
            } else if (tMeta instanceof EnchantmentStorageMeta tesm
                    && cMeta instanceof EnchantmentStorageMeta cesm) {
                if (!tesm.getStoredEnchants().isEmpty()
                        && !tesm.getStoredEnchants().equals(cesm.getStoredEnchants())) continue;
            }
            count += i.getAmount();
        }
        return count;
    }

    private ItemStack buildPriceInfo(EconomyProvider eco, double totalPrice, int playerHas, Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Sell price: &6" + eco.format(priceCalc.getSellPrice(item, player, 1)) + " " + eco.getCurrencyName());
        lore.add("&7Quantity:   &fx" + selectedQty);
        lore.add("&7You earn:   &6" + eco.format(totalPrice) + " " + eco.getCurrencyName());
        lore.add("");
        lore.add("&7You have: &fx" + playerHas);
        return new ItemBuilder(Material.GOLD_NUGGET).name("&6&lPrice Info").lore(lore).build();
    }
}
