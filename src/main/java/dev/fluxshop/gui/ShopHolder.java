package dev.fluxshop.gui;

import dev.fluxshop.model.Shop;
import dev.fluxshop.model.ShopCategory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * InventoryHolder tagging a FluxShop GUI so we can identify it in events.
 */
public class ShopHolder implements InventoryHolder {

    private final Shop          shop;
    private final ShopCategory  category;
    private Inventory           inventory;

    public ShopHolder(Shop shop, ShopCategory category) {
        this.shop     = shop;
        this.category = category;
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public void      setInventory(Inventory inv) { this.inventory = inv; }

    public Shop         getShop()     { return shop; }
    public ShopCategory getCategory() { return category; }
}
