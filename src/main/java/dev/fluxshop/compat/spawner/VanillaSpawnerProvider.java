package dev.fluxshop.compat.spawner;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Fallback spawner provider using vanilla NBT/PDC.
 * Works without any spawner plugin installed.
 */
public class VanillaSpawnerProvider implements SpawnerProvider {

    @Override public String getId() { return "vanilla"; }

    @Override
    public ItemStack createSpawnerItem(String entityType) {
        Material mat = spawnerMaterial();
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat);
        item = FluxShopPlugin.getInstance().getNMSHandler().setSpawnerEntityType(item, entityType);
        item = FluxShopPlugin.getInstance().getNMSHandler().setNBTString(item, "fluxshop_spawner_type", entityType.toUpperCase());
        return item;
    }

    @Override
    public String getEntityType(ItemStack item) {
        if (!isSpawner(item)) return null;
        String nbt = FluxShopPlugin.getInstance().getNMSHandler().getNBTString(item, "fluxshop_spawner_type");
        if (nbt != null) return nbt;
        return FluxShopPlugin.getInstance().getNMSHandler().getSpawnerEntityType(item);
    }

    @Override
    public boolean isSpawner(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.equals("SPAWNER") || name.equals("MOB_SPAWNER");
    }

    private Material spawnerMaterial() {
        try { return Material.valueOf("SPAWNER"); }
        catch (IllegalArgumentException e) {
            try { return Material.valueOf("MOB_SPAWNER"); }
            catch (IllegalArgumentException e2) { return null; }
        }
    }
}
