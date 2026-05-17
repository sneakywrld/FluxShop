package dev.fluxshop.api.event;

import dev.fluxshop.model.Shop;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player opens a FluxShop shop GUI.
 * Cancellable — prevents the GUI from opening.
 */
public class ShopOpenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Shop   shop;
    private       boolean cancelled;

    public ShopOpenEvent(Player player, Shop shop) {
        this.player = player;
        this.shop   = shop;
    }

    public Player getPlayer() { return player; }
    public Shop   getShop()   { return shop; }

    @Override public boolean isCancelled()          { return cancelled; }
    @Override public void    setCancelled(boolean c){ this.cancelled = c; }
    @Override public HandlerList getHandlers()       { return HANDLERS; }
    public static HandlerList getHandlerList()       { return HANDLERS; }
}
