package dev.fluxshop.compat.spawner;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/** UltimateStacker spawner provider (reflection-based). */
public class UltimateStackerProvider implements SpawnerProvider {
    @Override public String getId() { return "ultimatestacker"; }

    @Override
    public ItemStack createSpawnerItem(String entityType) {
        try {
            Class<?> api = Class.forName("com.songoda.ultimatestacker.UltimateStacker");
            Object inst = Bukkit.getPluginManager().getPlugin("UltimateStacker");
            Method m = api.getMethod("getSpawnerItemStack", EntityType.class, int.class);
            return (ItemStack) m.invoke(inst, EntityType.valueOf(entityType.toUpperCase()), 1);
        } catch (Exception e) { return null; }
    }

    @Override
    public String getEntityType(ItemStack item) {
        try {
            Class<?> api = Class.forName("com.songoda.ultimatestacker.UltimateStacker");
            Object inst = Bukkit.getPluginManager().getPlugin("UltimateStacker");
            Method m = api.getMethod("getSpawnerEntityType", ItemStack.class);
            Object result = m.invoke(inst, item);
            return result != null ? result.toString() : null;
        } catch (Exception e) { return null; }
    }

    @Override
    public boolean isSpawner(ItemStack item) { return getEntityType(item) != null; }
}
