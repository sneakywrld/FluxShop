package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.Shop;
import dev.fluxshop.model.ShopCategory;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.shop.PriceCalculator;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Choice screen shown to Bedrock/GeyserMC players instead of left/right-click routing.
 *
 * <p>Bedrock players (mobile and controller) have no reliable right-click, so using
 * right-click = sell is inaccessible to them. This screen presents explicit "Buy" and
 * "Sell" buttons they can tap or select with a controller.
 */
public class BedrockItemChoiceGui extends FluxGui {

    private static final int SLOT_BUY     = 11;
    private static final int SLOT_PREVIEW = 13;
    private static final int SLOT_SELL    = 15;
    private static final int SLOT_BACK    = 22;

    private final FluxShopPlugin  plugin;
    private final Shop            shop;
    private final ShopCategory    category;
    private final ShopItem        item;
    private final PriceCalculator priceCalc;

    public BedrockItemChoiceGui(FluxShopPlugin plugin, Shop shop, ShopCategory category, ShopItem item) {
        this.plugin    = plugin;
        this.shop      = shop;
        this.category  = category;
        this.item      = item;
        this.priceCalc = new PriceCalculator(plugin);
    }

    @Override
    public void open(Player player) {
        String itemName = item.getCustomName() != null
            ? item.getCustomName()
            : item.getDisplayItem().getType().name().charAt(0)
                + item.getDisplayItem().getType().name().substring(1).toLowerCase().replace('_', ' ');

        String title = plugin.getMessageManager().format("&7» &f" + itemName, player);

        GuiBuilder builder = new GuiBuilder(3, title)
            .border(Material.BLACK_STAINED_GLASS_PANE, " ")
            .fill(Material.GRAY_STAINED_GLASS_PANE, " ");

        // Item preview in the centre
        builder.set(SLOT_PREVIEW, item.getDisplayItem().clone());

        // Buy button
        if (item.isBuyable()) {
            var eco = plugin.getEconomyRegistry().get(item.getEconomyProvider());
            if (eco == null) eco = plugin.getEconomyRegistry().getDefault();
            String buyPrice = eco != null
                ? eco.format(priceCalc.getBuyPrice(item, player, 1)) + " " + eco.getCurrencyName()
                : String.format("$%.2f", priceCalc.getBuyPrice(item, player, 1));
            builder.set(SLOT_BUY, new ItemBuilder(Material.LIME_CONCRETE)
                .name(plugin.getMessageManager().format("&a&l✔ Buy", player))
                .lore(
                    plugin.getMessageManager().format("&7Price: &a" + buyPrice, player),
                    "",
                    plugin.getMessageManager().format("&eTap to buy", player)
                )
                .build());
        } else {
            builder.set(SLOT_BUY, new ItemBuilder(Material.GRAY_CONCRETE)
                .name(plugin.getMessageManager().format("&7Buy: &cNot available", player))
                .build());
        }

        // Sell button
        if (item.isSellable()) {
            var eco = plugin.getEconomyRegistry().get(item.getEconomyProvider());
            if (eco == null) eco = plugin.getEconomyRegistry().getDefault();
            String sellPrice = eco != null
                ? eco.format(priceCalc.getSellPrice(item, player, 1)) + " " + eco.getCurrencyName()
                : String.format("$%.2f", priceCalc.getSellPrice(item, player, 1));
            builder.set(SLOT_SELL, new ItemBuilder(Material.GOLD_BLOCK)
                .name(plugin.getMessageManager().format("&6&l$ Sell", player))
                .lore(
                    plugin.getMessageManager().format("&7Price: &6" + sellPrice, player),
                    "",
                    plugin.getMessageManager().format("&eTap to sell", player)
                )
                .build());
        } else {
            builder.set(SLOT_SELL, new ItemBuilder(Material.GRAY_CONCRETE)
                .name(plugin.getMessageManager().format("&7Sell: &cNot available", player))
                .build());
        }

        // Back button
        builder.set(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(plugin.getMessageManager().format("&7◁ Back", player))
            .lore(plugin.getMessageManager().format("&7Return to the category.", player))
            .build());

        inventory = builder.build(new ShopHolder(shop, category));
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == SLOT_BUY && item.isBuyable()) {
            plugin.getGuiManager().open(player, new BuyGui(plugin, shop, category, item));
            return;
        }
        if (slot == SLOT_SELL && item.isSellable()) {
            plugin.getGuiManager().open(player, new SellGui(plugin, shop, category, item));
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().goBack(player);
        }
    }
}
