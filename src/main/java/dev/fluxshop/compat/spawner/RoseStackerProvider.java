package dev.fluxshop.compat.spawner;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Spawner provider for RoseStacker.
 * All API access is via reflection to tolerate API version changes.
 */
public class RoseStackerProvider implements SpawnerProvider {

    @Override public String getId() { return "rosestacker"; }

    @Override
    public ItemStack createSpawnerItem(String entityType) {
        try {
            Class<?> apiClass = Class.forName("dev.rosewood.rosestacker.api.RoseStackerAPI");
            Object   api      = apiClass.getMethod("getInstance").invoke(null);
            EntityType et = EntityType.valueOf(entityType.toUpperCase());

            // Try getSpawnerStackAsItem(EntityType, int) first
            for (Method m : api.getClass().getMethods()) {
                if (m.getName().toLowerCase().contains("spawner") &&
                    m.getName().toLowerCase().contains("item") &&
                    m.getParameterCount() == 2) {
                    try {
                        Object result = m.invoke(api, et, 1);
                        if (result instanceof ItemStack is) return is;
                    } catch (Exception ignored) {}
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getEntityType(ItemStack item) {
        if (!isSpawner(item)) return null;
        try {
            Class<?> apiClass = Class.forName("dev.rosewood.rosestacker.api.RoseStackerAPI");
            Object   api      = apiClass.getMethod("getInstance").invoke(null);
            for (Method m : api.getClass().getMethods()) {
                if (m.getName().toLowerCase().contains("entitytype") &&
                    m.getParameterCount() == 1 &&
                    ItemStack.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    Object result = m.invoke(api, item);
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
