package dev.fluxshop.compat;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens for RealisticSeasons {@code SeasonChangeEvent} and broadcasts a
 * configurable shop-price announcement when the season changes.
 *
 * <p>The event class is handled via reflection (generic {@link org.bukkit.event.Event}
 * catch) so there is no compile-time dependency on RealisticSeasons.
 *
 * <p>Registered by {@link CompatManager} when RealisticSeasons is detected.
 * Announcement is controlled by {@code realistic-seasons.announce-change} in config.yml.
 */
public class RealisticSeasonsListener implements Listener {

    private final FluxShopPlugin plugin;

    public RealisticSeasonsListener(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Generic event handler that fires for every Bukkit event.
     * We filter to {@code SeasonChangeEvent} by simple class name and extract
     * the new season name via reflection.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSeasonChange(org.bukkit.event.Event event) {
        if (!event.getClass().getSimpleName().equals("SeasonChangeEvent")) return;
        if (!plugin.getConfigManager().getBoolean("realistic-seasons.announce-change", true)) return;

        try {
            // RealisticSeasons v1+: SeasonChangeEvent#getSeason() → Season enum
            Object season = event.getClass().getMethod("getSeason").invoke(event);
            String seasonName = season.toString();
            // Pretty-print: "SUMMER" → "Summer"
            String pretty = seasonName.substring(0, 1).toUpperCase()
                + seasonName.substring(1).toLowerCase();

            String msg = plugin.getConfigManager().getString(
                "realistic-seasons.change-message",
                "&b[FluxShop] &fThe season has changed to &e{season}&f! Shop prices have been updated.");
            msg = msg.replace("{season}", pretty);
            final String broadcast = plugin.getMessageManager().colorize(msg);
            // Schedule on main thread — event may fire async in some RS versions
            Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.broadcastMessage(broadcast));
        } catch (Exception ignored) {
            // Silently ignore — API mismatch should not produce console spam
        }
    }
}
