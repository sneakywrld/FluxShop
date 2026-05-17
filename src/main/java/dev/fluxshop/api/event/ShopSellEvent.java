package dev.fluxshop.api.event;

import dev.fluxshop.model.ShopItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired before a player completes a sell transaction in FluxShop.
 * Cancelling this event prevents the sale.
 */
public class ShopSellEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player   player;
    private final ShopItem item;
    private final int      quantity;
    private       double   finalPrice;
    private final String   economyId;
    private       boolean  cancelled;

    public ShopSellEvent(Player player, ShopItem item, int quantity, double finalPrice, String economyId) {
        this.player     = player;
        this.item       = item;
        this.quantity   = quantity;
        this.finalPrice = finalPrice;
        this.economyId  = economyId;
    }

    public Player   getPlayer()   { return player; }
    public ShopItem getItem()     { return item; }
    public int      getQuantity() { return quantity; }

    public double getFinalPrice()             { return finalPrice; }
    public void   setFinalPrice(double price) { this.finalPrice = price; }

    public String getEconomyId() { return economyId; }

    @Override public boolean isCancelled()          { return cancelled; }
    @Override public void    setCancelled(boolean c){ this.cancelled = c; }
    @Override public HandlerList getHandlers()       { return HANDLERS; }
    public static HandlerList getHandlerList()       { return HANDLERS; }
}
