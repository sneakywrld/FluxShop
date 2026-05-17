package dev.fluxshop.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * Base contract for all FluxShop GUI screens.
 *
 * <p>Each screen builds its own inventory and handles its own click events.
 * Navigation is managed by {@link GuiManager}.
 */
public abstract class FluxGui {

    protected Inventory inventory;

    /** Builds and opens the GUI for the given player. */
    public abstract void open(Player player);

    /** Handles a click event within this GUI's inventory. */
    public abstract void handleClick(InventoryClickEvent event);

    /**
     * Called when the player closes this GUI (e.g. presses Escape or is closed programmatically).
     * Override to perform cleanup such as returning items or unregistering listeners.
     * Default: no-op.
     */
    public void onClose(Player player) {}

    /** Returns the backing inventory. */
    public Inventory getInventory() {
        return inventory;
    }
}
