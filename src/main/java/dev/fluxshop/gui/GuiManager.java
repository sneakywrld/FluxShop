package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks open GUIs and routes click events to the correct handler.
 *
 * <p>Each player has a back-navigation stack so {@code BACK} actions
 * can pop to the previous screen seamlessly.
 */
public class GuiManager {

    private final FluxShopPlugin plugin;

    /** Map of player UUID → currently open FluxGui */
    private final Map<UUID, FluxGui> openGuis = new ConcurrentHashMap<>();
    /** Back-navigation stack per player */
    private final Map<UUID, Deque<FluxGui>> navStack = new ConcurrentHashMap<>();

    public GuiManager(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Opening ───────────────────────────────────────────────────────────────

    /**
     * Opens a GUI for a player, adding the current screen to the back-stack.
     */
    public void open(Player player, FluxGui gui) {
        UUID uuid = player.getUniqueId();

        // Push current GUI to back-stack
        FluxGui current = openGuis.get(uuid);
        if (current != null) {
            navStack.computeIfAbsent(uuid, k -> new ArrayDeque<>()).push(current);
        }

        openGuis.put(uuid, gui);
        gui.open(player);
    }

    /**
     * Opens a GUI without pushing the current one to the back-stack.
     * Use this for "replace" navigation (e.g. switching pages).
     */
    public void openReplace(Player player, FluxGui gui) {
        UUID uuid = player.getUniqueId();
        FluxGui current = openGuis.get(uuid);
        openGuis.put(uuid, gui);
        // Notify the departing GUI before switching.  The InventoryCloseEvent that fires
        // when gui.open() opens a new inventory will no longer find the departing GUI in
        // openGuis or navStack, so notifyClosed() would skip it — call onClose() here
        // explicitly to ensure cleanup (e.g. SellDropGui returning items, animation cancel).
        if (current != null) current.onClose(player);
        gui.open(player);
    }

    /**
     * Navigates back to the previous screen in the stack.
     * If there is no previous screen, closes the GUI.
     */
    public void goBack(Player player) {
        UUID  uuid  = player.getUniqueId();
        Deque<FluxGui> stack = navStack.get(uuid);
        if (stack == null || stack.isEmpty()) {
            // closeInventory() fires InventoryCloseEvent synchronously; notifyClosed()
            // handles onClose() for this path — do NOT call it here to avoid double-call.
            player.closeInventory();
            openGuis.remove(uuid);
            return;
        }
        FluxGui current = openGuis.get(uuid);
        FluxGui prev = stack.pop();
        openGuis.put(uuid, prev);
        // Same as openReplace: departing GUI's onClose() won't be triggered by the event
        // system once openGuis has been updated, so call it explicitly now.
        if (current != null) current.onClose(player);
        prev.open(player);
    }

    // ── Closing ───────────────────────────────────────────────────────────────

    public void closeAll() {
        openGuis.forEach((uuid, gui) -> {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) p.closeInventory();
        });
        openGuis.clear();
        navStack.clear();
    }

    public void onClose(Player player) {
        openGuis.remove(player.getUniqueId());
        navStack.remove(player.getUniqueId());
    }

    /** Called when the player's open GUI inventory is closed (allows GUIs to do cleanup). */
    public void notifyClosed(Player player, org.bukkit.inventory.Inventory inv) {
        UUID uuid = player.getUniqueId();
        FluxGui current = openGuis.get(uuid);

        // If the inventory being closed belongs to the currently-tracked GUI, remove it.
        if (current != null && current.getInventory() != null && current.getInventory().equals(inv)) {
            openGuis.remove(uuid);
            navStack.remove(uuid);
            current.onClose(player);
            return;
        }

        // The inventory belongs to a GUI that was pushed off the stack (e.g. a new GUI was
        // opened on top via GuiManager.open()). Find it in the nav-stack and call onClose so
        // it can return items (critical for SellDropGui).
        Deque<FluxGui> stack = navStack.get(uuid);
        if (stack != null) {
            stack.removeIf(g -> {
                if (g.getInventory() != null && g.getInventory().equals(inv)) {
                    g.onClose(player);
                    return true;
                }
                return false;
            });
        }
    }

    /** Removes all state for a player (called on quit or forced close). */
    public void clearPlayer(Player player) {
        FluxGui gui = openGuis.remove(player.getUniqueId());
        navStack.remove(player.getUniqueId());
        if (gui != null) gui.onClose(player);
    }

    // ── Click routing ─────────────────────────────────────────────────────────

    /**
     * Routes an inventory click event to the open GUI's handler.
     *
     * @return {@code true} if the event was handled (and should be cancelled)
     */
    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return false;
        FluxGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return false;
        if (gui.getInventory() == null || !gui.getInventory().equals(event.getInventory())) return false;
        gui.handleClick(event);
        return true;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public FluxGui getOpenGui(Player player) {
        return openGuis.get(player.getUniqueId());
    }

    public boolean isFluxGui(Inventory inv) {
        if (openGuis.values().stream().anyMatch(g -> g.getInventory() != null && g.getInventory().equals(inv)))
            return true;
        // Also check nav-stacks so GUIs displaced by a new open() still receive onClose().
        return navStack.values().stream()
            .anyMatch(stack -> stack.stream().anyMatch(g -> g.getInventory() != null && g.getInventory().equals(inv)));
    }

    public boolean hasOpenGui(Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }
}
