package dev.fluxshop.compat.spawner;

import org.bukkit.inventory.ItemStack;

/** SpawnerMeta — stores entity type via item meta/PDC. Uses vanilla fallback. */
public class SpawnerMetaProvider extends VanillaSpawnerProvider {
    @Override public String getId() { return "spawnermeta"; }
}
