package dev.fluxshop.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single purchasable/sellable item within a {@link ShopCategory}.
 *
 * <p>All prices are in the category's (or item's override) economy provider.
 * A price of -1 means the action is not available (can't buy / can't sell).
 */
public class ShopItem {

    // ── Identity ──────────────────────────────────────────────────────────────
    private String    id;          // Unique within the category
    private String    shopId;
    private String    categoryId;

    // ── Display ───────────────────────────────────────────────────────────────
    private ItemStack displayItem; // The resolved ItemStack shown in GUIs
    private String    customName;  // Override display name (null = use item name)
    private List<String> customLore = new ArrayList<>();

    // ── Prices ────────────────────────────────────────────────────────────────
    /** Buy price (-1 = not buyable). */
    private double buyPrice  = -1;
    /** Sell price (-1 = not sellable). */
    private double sellPrice = -1;
    /** Economy provider id override (null = inherit from category → shop → default). */
    private String economyProvider;

    // ── Custom item source ────────────────────────────────────────────────────
    /** MythicMobs item id, if this is a mythic item. */
    private String mythicItemId;
    /** ItemsAdder namespaced id (e.g. "mypack:diamond_sword"). */
    private String itemsAdderId;
    /** Oraxen item id. */
    private String oraxenId;
    /** Spawner entity type (e.g. "ZOMBIE"). Non-null means this is a spawner item. */
    private String spawnerEntityType;

    // ── Commands on purchase/sale ─────────────────────────────────────────────
    /** Commands run as CONSOLE when player buys this item. {player} placeholder available. */
    private List<String> onBuyCommands  = new ArrayList<>();
    /** Commands run as CONSOLE when player sells this item. */
    private List<String> onSellCommands = new ArrayList<>();

    // ── Permissions ───────────────────────────────────────────────────────────
    /** Permission node required to buy (null = no permission required). */
    private String buyPermission;
    /** Permission node required to sell. */
    private String sellPermission;

    // ── Stock ─────────────────────────────────────────────────────────────────
    /** Global maximum stock across all players (-1 = unlimited). */
    private int globalStock = -1;
    /** Per-player purchase limit per restock cycle (-1 = unlimited). */
    private int playerLimit = -1;
    /** How often (in seconds) player limits reset (0 = never). */
    private int playerLimitResetSeconds = 0;
    /** How often (in seconds) global stock auto-restocks (0 = never). */
    private int autoRestockSeconds = 0;

    // ── Dynamic pricing ───────────────────────────────────────────────────────
    private boolean dynamicPricingEnabled = false;
    private double  dynamicMultiplier     = 1.0;

    // ── Seasonal pricing ──────────────────────────────────────────────────────
    /** Season name → price multiplier. */
    private Map<String, Double> seasonModifiers = new HashMap<>();

    // ── Enchantments ──────────────────────────────────────────────────────────
    /** Map of enchantment name → level for items that include enchantments. */
    private Map<String, Integer> enchantments = new HashMap<>();
    /** If true, enchantment levels can exceed vanilla maximums. */
    private boolean allowUnsafeEnchants = false;

    // ── Misc ──────────────────────────────────────────────────────────────────
    /** Slot in the category GUI (0-indexed). -1 = auto-assign. */
    private int guiSlot = -1;
    /** If true, item shows in the GUI but cannot be interacted with. */
    private boolean displayOnly = false;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String    getId()              { return id; }
    public void      setId(String id)     { this.id = id; }

    public String    getShopId()          { return shopId; }
    public void      setShopId(String s)  { this.shopId = s; }

    public String    getCategoryId()          { return categoryId; }
    public void      setCategoryId(String c)  { this.categoryId = c; }

    public ItemStack getDisplayItem()          { return displayItem; }
    public void      setDisplayItem(ItemStack i){ this.displayItem = i; }

    public String    getCustomName()           { return customName; }
    public void      setCustomName(String n)   { this.customName = n; }

    public List<String> getCustomLore()        { return customLore; }
    public void         setCustomLore(List<String> l){ this.customLore = l; }

    public double getBuyPrice()            { return buyPrice; }
    public void   setBuyPrice(double p)    { this.buyPrice = p; }

    public double getSellPrice()           { return sellPrice; }
    public void   setSellPrice(double p)   { this.sellPrice = p; }

    public boolean isBuyable()             { return buyPrice >= 0; }
    public boolean isSellable()            { return sellPrice >= 0; }

    public String getEconomyProvider()     { return economyProvider; }
    public void   setEconomyProvider(String e){ this.economyProvider = e; }

    public String getMythicItemId()        { return mythicItemId; }
    public void   setMythicItemId(String m){ this.mythicItemId = m; }

    public String getItemsAdderId()        { return itemsAdderId; }
    public void   setItemsAdderId(String i){ this.itemsAdderId = i; }

    public String getOraxenId()            { return oraxenId; }
    public void   setOraxenId(String o)    { this.oraxenId = o; }

    public String getSpawnerEntityType()        { return spawnerEntityType; }
    public void   setSpawnerEntityType(String t){ this.spawnerEntityType = t; }
    public boolean isSpawnerItem()              { return spawnerEntityType != null; }

    public List<String> getOnBuyCommands()      { return onBuyCommands; }
    public void         setOnBuyCommands(List<String> c){ this.onBuyCommands = c; }

    public List<String> getOnSellCommands()     { return onSellCommands; }
    public void         setOnSellCommands(List<String> c){ this.onSellCommands = c; }

    public String getBuyPermission()            { return buyPermission; }
    public void   setBuyPermission(String p)    { this.buyPermission = p; }

    public String getSellPermission()           { return sellPermission; }
    public void   setSellPermission(String p)   { this.sellPermission = p; }

    public int  getGlobalStock()            { return globalStock; }
    public void setGlobalStock(int s)       { this.globalStock = s; }

    public int  getPlayerLimit()            { return playerLimit; }
    public void setPlayerLimit(int l)       { this.playerLimit = l; }

    public int  getPlayerLimitResetSeconds(){ return playerLimitResetSeconds; }
    public void setPlayerLimitResetSeconds(int s){ this.playerLimitResetSeconds = s; }

    public int  getAutoRestockSeconds()     { return autoRestockSeconds; }
    public void setAutoRestockSeconds(int s){ this.autoRestockSeconds = s; }

    public boolean isDynamicPricingEnabled()    { return dynamicPricingEnabled; }
    public void    setDynamicPricingEnabled(boolean b){ this.dynamicPricingEnabled = b; }

    public double getDynamicMultiplier()         { return dynamicMultiplier; }
    public void   setDynamicMultiplier(double m) { this.dynamicMultiplier = m; }

    public Map<String, Double> getSeasonModifiers()        { return seasonModifiers; }
    public void                setSeasonModifiers(Map<String, Double> m){ this.seasonModifiers = m; }

    public Map<String, Integer> getEnchantments()          { return enchantments; }
    public void                 setEnchantments(Map<String, Integer> e){ this.enchantments = e; }

    public boolean isAllowUnsafeEnchants()       { return allowUnsafeEnchants; }
    public void    setAllowUnsafeEnchants(boolean b){ this.allowUnsafeEnchants = b; }

    public int  getGuiSlot()            { return guiSlot; }
    public void setGuiSlot(int s)       { this.guiSlot = s; }

    public boolean isDisplayOnly()      { return displayOnly; }
    public void    setDisplayOnly(boolean b){ this.displayOnly = b; }
}
