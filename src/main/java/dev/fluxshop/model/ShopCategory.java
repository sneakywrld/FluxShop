package dev.fluxshop.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A section within a {@link Shop} — groups related items together (e.g. "Blocks", "Potions").
 */
public class ShopCategory {

    private String    id;
    private String    shopId;

    // GUI display
    private String    displayName;
    private ItemStack icon;
    private int       iconSlot = -1;  // Slot in the main shop menu (-1 = auto-assign)
    private String    permission;     // Permission to view/access this category (null = open)

    // Category page title
    private String    pageTitle;
    private int       rows = 6;

    // Fill & border materials (overrides GUI defaults)
    private String fillMaterial;
    private String borderMaterial;

    // Economy override for all items in this category
    private String economyProvider;

    // The actual items
    private final List<ShopItem> items = new ArrayList<>();

    // Whether this category is hidden (only accessible via /shop <id>)
    private boolean hidden = false;

    // Enabled / disabled
    private boolean enabled = true;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String    getId()           { return id; }
    public void      setId(String id)  { this.id = id; }

    public String    getShopId()       { return shopId; }
    public void      setShopId(String s){ this.shopId = s; }

    public String    getDisplayName()      { return displayName; }
    public void      setDisplayName(String n){ this.displayName = n; }

    public ItemStack getIcon()             { return icon; }
    public void      setIcon(ItemStack i)  { this.icon = i; }

    public int  getIconSlot()          { return iconSlot; }
    public void setIconSlot(int s)     { this.iconSlot = s; }

    public String getPermission()      { return permission; }
    public void   setPermission(String p){ this.permission = p; }

    public String getPageTitle()       { return pageTitle; }
    public void   setPageTitle(String t){ this.pageTitle = t; }

    public int  getRows()              { return rows; }
    public void setRows(int r)         { this.rows = r; }

    public String getFillMaterial()    { return fillMaterial; }
    public void   setFillMaterial(String m){ this.fillMaterial = m; }

    public String getBorderMaterial()  { return borderMaterial; }
    public void   setBorderMaterial(String m){ this.borderMaterial = m; }

    public String getEconomyProvider() { return economyProvider; }
    public void   setEconomyProvider(String e){ this.economyProvider = e; }

    public List<ShopItem> getItems()   { return items; }
    public void addItem(ShopItem item) { items.add(item); }

    public boolean isHidden()          { return hidden; }
    public void    setHidden(boolean h){ this.hidden = h; }

    public boolean isEnabled()         { return enabled; }
    public void    setEnabled(boolean e){ this.enabled = e; }
}
