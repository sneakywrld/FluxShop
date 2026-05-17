package dev.fluxshop.api.event;

import dev.fluxshop.model.AuctionListing;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired (informational, not cancellable) when an auction listing is sold.
 */
public class AuctionSoldEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final AuctionListing listing;
    private final UUID           buyerUuid;
    private final double         finalPrice;

    public AuctionSoldEvent(AuctionListing listing, UUID buyerUuid, double finalPrice) {
        this.listing    = listing;
        this.buyerUuid  = buyerUuid;
        this.finalPrice = finalPrice;
    }

    public AuctionListing getListing()    { return listing; }
    public UUID           getBuyerUuid()  { return buyerUuid; }
    public double         getFinalPrice() { return finalPrice; }

    @Override public HandlerList getHandlers()  { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}
