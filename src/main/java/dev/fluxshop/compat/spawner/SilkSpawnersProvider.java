package dev.fluxshop.compat.spawner;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Spawner provider for SilkSpawners (both v5/v6 and v7+).
 * Uses reflection to avoid version-specific compile dependencies.
 */
public class SilkSpawnersProvider implements SpawnerProvider {

    private Object api;
    private Method createSpawnerMethod;
    private Method getEntityMethod;

    public SilkSpawnersProvider() {
        try {
            // Try v5/v6 API
            Class<?> apiClass = Class.forName("de.dustplanet.silkspawners.SilkSpawners");
            api = Bukkit.getPluginManager().getPlugin("SilkSpawners");
            createSpawnerMethod = apiClass.getMethod("createSpawnerItem", org.bukkit.entity.EntityType.class);
            getEntityMethod     = apiClass.getMethod("getSpawnerEntityType", ItemStack.class);
        } catch (Exception e1) {
            try {
                // Try newer SilkSpawners
                Class<?> apiClass = Class.forName("de.dustplanet.silkspawners.api.ISilkSpawnersAPI");
                api = Bukkit.getServicesManager().load(apiClass);
                createSpawnerMethod = apiClass.getMethod("createSpawnerItem", String.class);
                getEntityMethod     = apiClass.getMethod("getSpawnerEntityType", ItemStack.class);
            } catch (Exception e2) {
                api = null;
            }
        }
    }

    @Override public String getId() { return "silkspawners"; }

    @Override
    public ItemStack createSpawnerItem(String entityType) {
        if (api == null || createSpawnerMethod == null) return null;
        try {
            Object param = createSpawnerMethod.getParameterTypes()[0] == String.class
                ? entityType
                : org.bukkit.entity.EntityType.valueOf(entityType.toUpperCase());
            return (ItemStack) createSpawnerMethod.invoke(api, param);
        } catch (Exception e) { return null; }
    }

    @Override
    public String getEntityType(ItemStack item) {
        if (api == null || getEntityMethod == null) return null;
        try {
            Object result = getEntityMethod.invoke(api, item);
            return result != null ? result.toString() : null;
        } catch (Exception e) { return null; }
    }

    @Override
    public boolean isSpawner(ItemStack item) {
        return getEntityType(item) != null;
    }
}
