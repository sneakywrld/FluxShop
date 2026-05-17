package dev.fluxshop.util;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.compat.CompatManager;
import dev.fluxshop.compat.spawner.SpawnerProvider;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Creates and identifies spawner {@link ItemStack}s across all supported spawner plugins.
 *
 * <p>Delegates to the active {@link SpawnerProvider} (detected at startup via {@link CompatManager}).
 * Falls back to vanilla SPAWNER handling if no spawner plugin is installed.
 */
public final class SpawnerItemHelper {

    private SpawnerItemHelper() {}

    /**
     * Creates a spawner ItemStack for the given entity type using the active spawner plugin.
     *
     * @param entityType Bukkit entity type name (e.g. "ZOMBIE", "BLAZE")
     * @return a properly tagged spawner ItemStack
     */
    public static ItemStack createSpawner(String entityType) {
        SpawnerProvider provider = getProvider();
        if (provider != null) {
            ItemStack result = provider.createSpawnerItem(entityType);
            if (result != null) return result;
        }
        // Vanilla fallback — use NBT to tag entity type
        return createVanillaSpawner(entityType);
    }

    /**
     * Returns the entity type name encoded in a spawner ItemStack.
     * Returns {@code null} if the item is not a spawner.
     */
    public static String getEntityType(ItemStack item) {
        if (item == null) return null;
        SpawnerProvider provider = getProvider();
        if (provider != null) {
            String type = provider.getEntityType(item);
            if (type != null) return type;
        }
        // Vanilla NBT fallback
        return FluxShopPlugin.getInstance().getNMSHandler().getSpawnerEntityType(item);
    }

    /**
     * Returns {@code true} if the given item is a spawner item, regardless of plugin.
     */
    public static boolean isSpawner(ItemStack item) {
        if (item == null) return false;
        // Check vanilla material first
        String matName = item.getType().name();
        if (matName.equals("SPAWNER") || matName.equals("MOB_SPAWNER")) return true;
        // Ask the provider
        SpawnerProvider provider = getProvider();
        return provider != null && provider.isSpawner(item);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static SpawnerProvider getProvider() {
        return FluxShopPlugin.getInstance().getCompatManager().getSpawnerProvider();
    }

    private static ItemStack createVanillaSpawner(String entityType) {
        Material mat;
        try {
            mat = Material.valueOf("SPAWNER");
        } catch (IllegalArgumentException e) {
            //noinspection deprecation
            mat = Material.valueOf("MOB_SPAWNER"); // 1.8–1.12 name
        }
        ItemStack item = FluxShopPlugin.getInstance()
            .getNMSHandler()
            .setSpawnerEntityType(new ItemStack(mat), entityType);
        // Also tag via PDC/NBT so FluxShop can identify it later
        return FluxShopPlugin.getInstance()
            .getNMSHandler()
            .setNBTString(item, "fluxshop_spawner_type", entityType.toUpperCase());
    }
}
