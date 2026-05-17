package dev.fluxshop.compat.spawner;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

/** SmartSpawner — stores entity type in BlockStateMeta. Falls back to vanilla handling. */
public class SmartSpawnerProvider implements SpawnerProvider {
    @Override public String getId() { return "smartspawner"; }

    @Override
    public ItemStack createSpawnerItem(String entityType) {
        try {
            Material mat = Material.valueOf("SPAWNER");
            ItemStack item = new ItemStack(mat);
            if (item.getItemMeta() instanceof BlockStateMeta bsm) {
                if (bsm.getBlockState() instanceof org.bukkit.block.CreatureSpawner cs) {
                    cs.setSpawnedType(EntityType.valueOf(entityType.toUpperCase()));
                    bsm.setBlockState(cs);
                    item.setItemMeta(bsm);
                }
            }
            return item;
        } catch (Exception e) { return null; }
    }

    @Override
    public String getEntityType(ItemStack item) {
        if (!isSpawner(item)) return null;
        try {
            if (item.getItemMeta() instanceof BlockStateMeta bsm) {
                if (bsm.getBlockState() instanceof org.bukkit.block.CreatureSpawner cs) {
                    EntityType et = cs.getSpawnedType();
                    return et != null ? et.name() : null;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public boolean isSpawner(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().contains("SPAWNER");
    }
}
