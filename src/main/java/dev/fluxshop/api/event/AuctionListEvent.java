package dev.fluxshop.api.event;

import dev.fluxshop.model.AuctionListing;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player lists an item in the Auction House.
 */
public class AuctionListEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player         seller;
    private final AuctionListing listing;
    private       boolean        cancelled;

    public AuctionListEvent(Player seller, AuctionListing listing) {
        this.seller  = seller;
        this.listing = listing;
    }

    public Player         getSeller()  { return seller; }
    public AuctionListing getListing() { return listing; }

    @Override public boolean isCancelled()          { return cancelled; }
    @Override public void    setCancelled(boolean c){ this.cancelled = c; }
    @Override public HandlerList getHandlers()       { return HANDLERS; }
    public static HandlerList getHandlerList()       { return HANDLERS; }
}
