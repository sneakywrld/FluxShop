package dev.fluxshop.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of a single buy or sell transaction.
 */
public class Transaction {

    public enum Type { BUY, SELL }

    private final long    id;
    private final UUID    playerUuid;
    private final String  playerName;
    private final Type    type;
    private final String  shopId;
    private final String  categoryId;
    private final String  itemId;
    private final String  itemName;
    private final int     quantity;
    private final double  pricePerUnit;
    private final double  totalPrice;
    private final String  currency;
    private final Instant timestamp;

    public Transaction(long id, UUID playerUuid, String playerName, Type type,
                       String shopId, String categoryId, String itemId, String itemName,
                       int quantity, double pricePerUnit, String currency, Instant timestamp) {
        this.id           = id;
        this.playerUuid   = playerUuid;
        this.playerName   = playerName;
        this.type         = type;
        this.shopId       = shopId;
        this.categoryId   = categoryId;
        this.itemId       = itemId;
        this.itemName     = itemName;
        this.quantity     = quantity;
        this.pricePerUnit = pricePerUnit;
        this.totalPrice   = pricePerUnit * quantity;
        this.currency     = currency;
        this.timestamp    = timestamp;
    }

    public long    getId()           { return id; }
    public UUID    getPlayerUuid()   { return playerUuid; }
    public String  getPlayerName()   { return playerName; }
    public Type    getType()         { return type; }
    public String  getShopId()       { return shopId; }
    public String  getCategoryId()   { return categoryId; }
    public String  getItemId()       { return itemId; }
    public String  getItemName()     { return itemName; }
    public int     getQuantity()     { return quantity; }
    public double  getPricePerUnit() { return pricePerUnit; }
    public double  getTotalPrice()   { return totalPrice; }
    public String  getCurrency()     { return currency; }
    public Instant getTimestamp()    { return timestamp; }
}
