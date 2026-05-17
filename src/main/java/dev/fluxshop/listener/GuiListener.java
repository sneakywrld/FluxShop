package dev.fluxshop.listener;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.gui.GuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Routes inventory events to the GuiManager.
 * Also cleans up GUI state when a player disconnects.
 */
public class GuiListener implements Listener {

    private final FluxShopPlugin plugin;
    private final GuiManager     guiManager;

    public GuiListener(FluxShopPlugin plugin) {
        this.plugin     = plugin;
        this.guiManager = plugin.getGuiManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!guiManager.isFluxGui(event.getInventory())) return;

        // Always cancel item movement within a FluxShop GUI
        event.setCancelled(true);

        // Route to the GUI's click handler for every slot — even "empty" slots may be
        // navigation panes or have invisible items (AIR fillers used as click targets).
        guiManager.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!guiManager.isFluxGui(event.getInventory())) return;

        // Block drags into GUI slots
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!guiManager.isFluxGui(event.getInventory())) return;

        // Notify the GUI it was closed so it can clean up (e.g. SellDropGui returns items)
        if (guiManager.hasOpenGui(player)) {
            guiManager.notifyClosed(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        guiManager.clearPlayer(event.getPlayer());
    }
}
