package dev.fluxshop.api.event;

import dev.fluxshop.model.AuctionListing;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player places a bid on an auction listing.
 */
public class AuctionBidEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player         bidder;
    private final AuctionListing listing;
    private       double         bidAmount;
    private       boolean        cancelled;

    public AuctionBidEvent(Player bidder, AuctionListing listing, double bidAmount) {
        this.bidder    = bidder;
        this.listing   = listing;
        this.bidAmount = bidAmount;
    }

    public Player         getBidder()    { return bidder; }
    public AuctionListing getListing()   { return listing; }
    public double         getBidAmount() { return bidAmount; }
    public void           setBidAmount(double amount){ this.bidAmount = amount; }

    @Override public boolean isCancelled()          { return cancelled; }
    @Override public void    setCancelled(boolean c){ this.cancelled = c; }
    @Override public HandlerList getHandlers()       { return HANDLERS; }
    public static HandlerList getHandlerList()       { return HANDLERS; }
}
