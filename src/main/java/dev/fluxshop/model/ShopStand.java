package dev.fluxshop.model;

import dev.fluxshop.shop.ShopStandManager;
import org.bukkit.Location;

/**
 * Represents a physical shop-stand block registered in the world.
 *
 * <p>A shop stand is a regular block that opens a shop GUI when right-clicked.
 * Stand registrations are persisted by {@link ShopStandManager} in {@code stands.yml}.
 *
 * @param location the block location of the stand
 * @param shopId   the id of the shop this stand opens
 */
public record ShopStand(Location location, String shopId) {

    /**
     * Deserialises a stand from the YAML storage key ({@code "world,x,y,z"})
     * and its shop id value. Returns {@code null} if the world is not loaded
     * or the key is malformed.
     */
    public static ShopStand fromEntry(String locationKey, String shopId) {
        Location loc = ShopStandManager.deserialize(locationKey);
        if (loc == null) return null;
        return new ShopStand(loc, shopId);
    }

    /** Returns the serialised location key used in {@code stands.yml}. */
    public String locationKey() {
        return ShopStandManager.serialize(location);
    }
}
