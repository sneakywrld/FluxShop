package dev.fluxshop.gui;

import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for {@link Inventory}-based GUIs.
 *
 * <pre>{@code
 * Inventory inv = new GuiBuilder(6, "&6Shop")
 *     .border(Material.BLACK_STAINED_GLASS_PANE, " ")
 *     .set(22, new ItemBuilder(Material.DIAMOND).name("&bDiamond").build())
 *     .build(holder);
 * }</pre>
 */
public class GuiBuilder {

    private final int    rows;
    private final String title;
    private final Map<Integer, ItemStack> items = new HashMap<>();

    public GuiBuilder(int rows, String title) {
        this.rows  = Math.max(1, Math.min(6, rows));
        this.title = colorize(title);
    }

    /** Sets a specific slot. */
    public GuiBuilder set(int slot, ItemStack item) {
        if (slot >= 0 && slot < rows * 9) items.put(slot, item);
        return this;
    }

    /** Fills all border slots (row 0, row last, column 0, column 8). */
    public GuiBuilder border(Material material, String name) {
        ItemStack pane = ItemBuilder.pane(material, name);
        int size = rows * 9;
        for (int i = 0; i < 9; i++)        items.put(i, pane);         // top row
        for (int i = size-9; i < size; i++) items.put(i, pane);        // bottom row
        for (int r = 1; r < rows-1; r++) {
            items.put(r*9,   pane);                                      // left column
            items.put(r*9+8, pane);                                      // right column
        }
        return this;
    }

    /** Fills all empty slots with the given material. */
    public GuiBuilder fill(Material material, String name) {
        ItemStack filler = ItemBuilder.pane(material, name);
        for (int i = 0; i < rows * 9; i++) items.putIfAbsent(i, filler);
        return this;
    }

    /** Fills a specific row. */
    public GuiBuilder fillRow(int row, Material material, String name) {
        ItemStack pane = ItemBuilder.pane(material, name);
        for (int c = 0; c < 9; c++) items.put(row * 9 + c, pane);
        return this;
    }

    /** Sets all slots in the list. */
    public GuiBuilder setAll(int[] slots, ItemStack item) {
        for (int slot : slots) set(slot, item);
        return this;
    }

    /** Clears a slot (sets to air). */
    public GuiBuilder clear(int slot) {
        items.put(slot, new ItemStack(Material.AIR));
        return this;
    }

    /** Builds the inventory with the given holder. */
    public Inventory build(InventoryHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        items.forEach(inv::setItem);
        return inv;
    }

    /** Builds without a holder. */
    public Inventory build() {
        return build(null);
    }

    /** Applies all pending changes to an existing inventory (for updates without re-opening). */
    public void apply(Inventory inv) {
        items.forEach(inv::setItem);
    }

    /** Returns the total slot count. */
    public int size() { return rows * 9; }
    public int rows() { return rows; }
    public String title() { return title; }

    private String colorize(String s) {
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', s);
    }

    // ── Click action registry ─────────────────────────────────────────────────

    /** Associates a click handler with a slot (used by FluxGui subclasses). */
    public static class ClickMap {
        private final Map<Integer, Consumer<org.bukkit.event.inventory.InventoryClickEvent>> handlers = new HashMap<>();

        public ClickMap on(int slot, Consumer<org.bukkit.event.inventory.InventoryClickEvent> handler) {
            handlers.put(slot, handler);
            return this;
        }

        public Consumer<org.bukkit.event.inventory.InventoryClickEvent> get(int slot) {
            return handlers.get(slot);
        }

        public boolean has(int slot) { return handlers.containsKey(slot); }
    }
}
