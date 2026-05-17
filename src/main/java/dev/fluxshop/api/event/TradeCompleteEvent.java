package dev.fluxshop.api.event;

import dev.fluxshop.model.TradeOffer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired (informational, not cancellable) when a player trade completes successfully.
 */
public class TradeCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TradeOffer offer;

    public TradeCompleteEvent(TradeOffer offer) {
        this.offer = offer;
    }

    public TradeOffer getOffer() { return offer; }

    @Override public HandlerList getHandlers()  { return HANDLERS; }
    public static HandlerList getHandlerList()  { return HANDLERS; }
}
