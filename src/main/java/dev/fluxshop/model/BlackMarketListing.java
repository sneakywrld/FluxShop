package dev.fluxshop.model;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;

/**
 * A single item slot in the rotating Black Market.
 */
public class BlackMarketListing {

    private long      id;
    private ItemStack item;
    private double    price;
    private String    currency;
    private int       stock;          // Total available in this rotation
    private int       purchased;      // How many have been bought so far
    private int       maxPerPlayer;   // Per-player purchase limit (0 = unlimited)
    private Instant   nextRefresh;    // When this rotation ends/refreshes

    public long      getId()               { return id; }
    public void      setId(long id)        { this.id = id; }

    public ItemStack getItem()             { return item; }
    public void      setItem(ItemStack i)  { this.item = i; }

    public double    getPrice()            { return price; }
    public void      setPrice(double p)    { this.price = p; }

    public String    getCurrency()         { return currency; }
    public void      setCurrency(String c) { this.currency = c; }

    public int  getStock()                 { return stock; }
    public void setStock(int s)            { this.stock = s; }

    public int  getPurchased()             { return purchased; }
    public void setPurchased(int p)        { this.purchased = p; }

    public int  getRemainingStock()        { return Math.max(0, stock - purchased); }
    public boolean isInStock()             { return getRemainingStock() > 0; }

    public int  getMaxPerPlayer()          { return maxPerPlayer; }
    public void setMaxPerPlayer(int m)     { this.maxPerPlayer = m; }

    public Instant getNextRefresh()        { return nextRefresh; }
    public void    setNextRefresh(Instant i){ this.nextRefresh = i; }
}
