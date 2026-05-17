package dev.fluxshop.compat.spawner;

import org.bukkit.inventory.ItemStack;

/**
 * Abstraction over spawner plugin integrations.
 *
 * <p>Implementations for each supported spawner plugin handle the
 * creation and identification of spawner items in a plugin-specific way.
 */
public interface SpawnerProvider {

    /** Returns the id of this provider (e.g. "rosestacker", "silkspawners"). */
    String getId();

    /**
     * Creates a spawner ItemStack for the given entity type.
     *
     * @param entityType Bukkit entity type name (e.g. "ZOMBIE")
     * @return a properly tagged spawner ItemStack, or {@code null} if creation failed
     */
    ItemStack createSpawnerItem(String entityType);

    /**
     * Returns the entity type name stored in a spawner ItemStack.
     *
     * @return entity type name (e.g. "ZOMBIE"), or {@code null} if not a spawner
     */
    String getEntityType(ItemStack item);

    /**
     * Returns {@code true} if the given item is a spawner managed by this plugin.
     */
    boolean isSpawner(ItemStack item);
}
