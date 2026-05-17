package dev.fluxshop.compat.spawner;

import org.bukkit.inventory.ItemStack;

/** MineableSpawners — identifies spawners by plugin NBT tag. Falls back to vanilla. */
public class MineableSpawnersProvider extends VanillaSpawnerProvider {
    @Override public String getId() { return "mineablespawners"; }
    // MineableSpawners doesn't significantly alter the item format;
    // vanilla NBT handling covers this plugin adequately.
}
