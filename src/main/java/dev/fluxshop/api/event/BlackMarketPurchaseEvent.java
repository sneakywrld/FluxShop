package dev.fluxshop.api.event;

import dev.fluxshop.model.BlackMarketListing;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player purchases from the Black Market.
 */
public class BlackMarketPurchaseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player             player;
    private final BlackMarketListing listing;
    private       double             price;
    private       boolean            cancelled;

    public BlackMarketPurchaseEvent(Player player, BlackMarketListing listing, double price) {
        this.player  = player;
        this.listing = listing;
        this.price   = price;
    }

    public Player             getPlayer()  { return player; }
    public BlackMarketListing getListing() { return listing; }
    public double             getPrice()   { return price; }
    public void               setPrice(double price){ this.price = price; }

    @Override public boolean isCancelled()          { return cancelled; }
    @Override public void    setCancelled(boolean c){ this.cancelled = c; }
    @Override public HandlerList getHandlers()       { return HANDLERS; }
    public static HandlerList getHandlerList()       { return HANDLERS; }
}
