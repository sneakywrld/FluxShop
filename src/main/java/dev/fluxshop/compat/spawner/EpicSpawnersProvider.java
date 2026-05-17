package dev.fluxshop.compat.spawner;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/** EpicSpawners spawner provider (reflection-based). */
public class EpicSpawnersProvider implements SpawnerProvider {
    @Override public String getId() { return "epicspawners"; }

    @Override
    public ItemStack createSpawnerItem(String entityType) {
        try {
            Class<?> api = Class.forName("com.songoda.epicspawners.EpicSpawners");
            Object inst = Bukkit.getPluginManager().getPlugin("EpicSpawners");
            Method m = api.getMethod("getSpawnerItemStack", String.class, int.class);
            return (ItemStack) m.invoke(inst, entityType, 1);
        } catch (Exception e) { return null; }
    }

    @Override
    public String getEntityType(ItemStack item) {
        try {
            Class<?> api = Class.forName("com.songoda.epicspawners.EpicSpawners");
            Object inst = Bukkit.getPluginManager().getPlugin("EpicSpawners");
            Method m = api.getMethod("getSpawnerEntityType", ItemStack.class);
            Object r = m.invoke(inst, item);
            return r != null ? r.toString() : null;
        } catch (Exception e) { return null; }
    }

    @Override
    public boolean isSpawner(ItemStack item) { return getEntityType(item) != null; }
}
