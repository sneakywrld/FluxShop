package dev.fluxshop.model;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single listing on the Auction House.
 */
public class AuctionListing {

    public enum Status { ACTIVE, SOLD, EXPIRED, CANCELLED }

    private long      id;
    private UUID      sellerUuid;
    private String    sellerName;
    private ItemStack item;
    private double    startPrice;
    private double    buyNowPrice;    // -1 = no buy-now option
    private double    currentBid;
    private UUID      currentBidder;
    private String    currentBidderName;
    private String    currency;       // economy provider id
    private Instant   createdAt;
    private Instant   expiresAt;
    private Status    status = Status.ACTIVE;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public long      getId()               { return id; }
    public void      setId(long id)        { this.id = id; }

    public UUID      getSellerUuid()       { return sellerUuid; }
    public void      setSellerUuid(UUID u) { this.sellerUuid = u; }

    public String    getSellerName()       { return sellerName; }
    public void      setSellerName(String n){ this.sellerName = n; }

    public ItemStack getItem()             { return item; }
    public void      setItem(ItemStack i)  { this.item = i; }

    public double    getStartPrice()       { return startPrice; }
    public void      setStartPrice(double p){ this.startPrice = p; }

    public double    getBuyNowPrice()      { return buyNowPrice; }
    public void      setBuyNowPrice(double p){ this.buyNowPrice = p; }
    public boolean   hasBuyNow()           { return buyNowPrice > 0; }

    public double    getCurrentBid()       { return currentBid; }
    public void      setCurrentBid(double b){ this.currentBid = b; }

    public UUID      getCurrentBidder()    { return currentBidder; }
    public void      setCurrentBidder(UUID u){ this.currentBidder = u; }

    public String    getCurrentBidderName(){ return currentBidderName; }
    public void      setCurrentBidderName(String n){ this.currentBidderName = n; }

    public boolean   hasBid()              { return currentBidder != null; }

    public String    getCurrency()         { return currency; }
    public void      setCurrency(String c) { this.currency = c; }

    public Instant   getCreatedAt()        { return createdAt; }
    public void      setCreatedAt(Instant i){ this.createdAt = i; }

    public Instant   getExpiresAt()        { return expiresAt; }
    public void      setExpiresAt(Instant i){ this.expiresAt = i; }

    public Status    getStatus()           { return status; }
    public void      setStatus(Status s)   { this.status = s; }

    public boolean   isActive()            { return status == Status.ACTIVE; }
    public boolean   isExpired()           { return Instant.now().isAfter(expiresAt) && isActive(); }

    /**
     * Returns the minimum acceptable next bid.
     * If no bid has been placed, this equals the start price.
     */
    public double getMinNextBid(double incrementPercent) {
        if (!hasBid()) return startPrice;
        return currentBid * (1 + incrementPercent / 100.0);
    }
}
