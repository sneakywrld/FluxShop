package dev.fluxshop.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level shop container. Each shop corresponds to a YAML file in {@code shops/}.
 * A shop contains one or more {@link ShopCategory}s displayed in the main menu.
 */
public class Shop {

    private String    id;           // Unique identifier (filename without .yml)
    private String    displayName;  // Shown at top of main menu
    private ItemStack displayItem;  // Icon in the main menu
    private int       displaySlot = -1; // Slot in main menu grid (-1 = auto)
    private String    permission;   // Access permission (null = no restriction)

    // World restrictions (empty = all worlds)
    private List<String> allowedWorlds = new ArrayList<>();
    private List<String> blockedWorlds = new ArrayList<>();

    private final List<ShopCategory> categories = new ArrayList<>();

    private boolean enabled = true;

    // Economy provider override for all categories in this shop
    private String economyProvider;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String    getId()              { return id; }
    public void      setId(String id)     { this.id = id; }

    public String    getDisplayName()         { return displayName; }
    public void      setDisplayName(String n) { this.displayName = n; }

    public ItemStack getDisplayItem()         { return displayItem; }
    public void      setDisplayItem(ItemStack i){ this.displayItem = i; }

    public int  getDisplaySlot()          { return displaySlot; }
    public void setDisplaySlot(int s)     { this.displaySlot = s; }

    public String getPermission()         { return permission; }
    public void   setPermission(String p) { this.permission = p; }

    public List<String> getAllowedWorlds()     { return allowedWorlds; }
    public void         setAllowedWorlds(List<String> w){ this.allowedWorlds = w; }

    public List<String> getBlockedWorlds()    { return blockedWorlds; }
    public void         setBlockedWorlds(List<String> w){ this.blockedWorlds = w; }

    public List<ShopCategory> getCategories() { return categories; }
    public void addCategory(ShopCategory cat) { categories.add(cat); }

    public ShopCategory getCategory(String id) {
        return categories.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
    }

    public boolean isEnabled()            { return enabled; }
    public void    setEnabled(boolean e)  { this.enabled = e; }

    public String getEconomyProvider()    { return economyProvider; }
    public void   setEconomyProvider(String e){ this.economyProvider = e; }
}
