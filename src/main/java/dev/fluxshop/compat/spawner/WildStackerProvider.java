package dev.fluxshop.compat.spawner;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Spawner provider for WildStacker.
 * All API access is via reflection to tolerate API version changes.
 */
public class WildStackerProvider implements SpawnerProvider {

    @Override public String getId() { return "wildstacker"; }

    @Override
    public ItemStack createSpawnerItem(String entityType) {
        try {
            Class<?> apiClass = Class.forName("com.bgsoftware.wildstacker.api.WildStackerAPI");
            Object   plugin   = apiClass.getMethod("getWildStacker").invoke(null);
            Object   sysManager = plugin.getClass().getMethod("getSystemManager").invoke(plugin);
            EntityType et = EntityType.valueOf(entityType.toUpperCase());

            for (Method m : sysManager.getClass().getMethods()) {
                if (m.getName().toLowerCase().contains("spawner") &&
                    m.getName().toLowerCase().contains("item") &&
                    m.getParameterCount() == 2) {
                    try {
                        Object result = m.invoke(sysManager, et, 1);
                        if (result instanceof ItemStack is) return is;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public String getEntityType(ItemStack item) {
        if (!isSpawner(item)) return null;
        try {
            Class<?> apiClass = Class.forName("com.bgsoftware.wildstacker.api.WildStackerAPI");
            Object   plugin   = apiClass.getMethod("getWildStacker").invoke(null);
            Object   sysManager = plugin.getClass().getMethod("getSystemManager").invoke(plugin);

            for (Method m : sysManager.getClass().getMethods()) {
                if (m.getName().toLowerCase().contains("entitytype") &&
                    m.getParameterCount() == 1 &&
                    ItemStack.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    Object result = m.invoke(sysManager, item);
                    if (result instanceof EntityType et) return et.name();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public boolean isSpawner(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.equals("SPAWNER") || name.equals("MOB_SPAWNER");
    }
}
