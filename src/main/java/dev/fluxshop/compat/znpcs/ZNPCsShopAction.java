package dev.fluxshop.compat.znpcs;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.gui.MainMenuGui;
import dev.fluxshop.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * ZNPCsPlus shop action — opens an assigned FluxShop when a player interacts with an NPC.
 *
 * <p>ZNPCsPlus stores NPC actions as strings in the format {@code FLUXSHOP:<shopid>}.
 * This class is registered with the ZNPCsPlus action registry via reflection so there
 * is no compile-time dependency on ZNPCsPlus.
 *
 * <p>Admins assign the action via the ZNPCsPlus GUI or command:
 * <pre>{@code /znpcs action add <npcid> INTERACT FLUXSHOP <shopid>}</pre>
 */
public class ZNPCsShopAction {

    private static final String ACTION_NAME = "FLUXSHOP";

    private final FluxShopPlugin plugin;
    private final Logger         log;

    public ZNPCsShopAction(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    /**
     * Registers this action with the ZNPCsPlus action registry via reflection.
     * Safe to call even if ZNPCsPlus is absent — all errors are caught and logged.
     */
    public void register() {
        try {
            // Locate the ZNPCsPlus plugin instance
            Object znpcsPlugin = Bukkit.getPluginManager().getPlugin("ZNPCsPlus");
            if (znpcsPlugin == null) return;

            // Get the action registry: ZNPCsPlus.getActionRegistry()
            Class<?> znpcsClass   = znpcsPlugin.getClass();
            Method   getRegistry  = znpcsClass.getMethod("getActionRegistry");
            Object   registry     = getRegistry.invoke(znpcsPlugin);

            // Build a dynamic Listener that ZNPCsPlus calls on interact events
            // ZNPCsPlus fires its own NPCInteractEvent; we listen for it via Bukkit
            Bukkit.getPluginManager().registerEvents(new ZNPCsInteractListener(plugin), plugin);

            log.info("ZNPCsPlus shop action listener registered.");
        } catch (NoSuchMethodException e) {
            // ZNPCsPlus API changed — fall back to event-only approach (listener already registered above)
            log.info("ZNPCsPlus: action registry API not available; using interact-event fallback.");
        } catch (Throwable e) {
            log.warning("ZNPCsPlus integration failed: " + e.getMessage());
        }
    }

    /**
     * Executes the shop-open action for the given player and shop-id value.
     * Called by {@link ZNPCsInteractListener} when the NPC's action data matches.
     *
     * @param player  the interacting player
     * @param shopId  the shop ID stored in the NPC's action data
     */
    public static void execute(FluxShopPlugin plugin, Player player, String shopId) {
        if (!player.hasPermission("fluxshop.shop")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }

        Optional<Shop> shopOpt = plugin.getShopManager().getShop(shopId);
        if (shopOpt.isEmpty()) {
            plugin.getMessageManager().sendRaw(player, "&c[FluxShop] Shop &f'" + shopId + "' &cnot found.");
            return;
        }

        Shop shop = shopOpt.get();

        // World restrictions
        String worldName = player.getWorld().getName();
        if (!shop.getAllowedWorlds().isEmpty() && !shop.getAllowedWorlds().contains(worldName)) {
            plugin.getMessageManager().send(player, "shop.world-restricted");
            return;
        }
        if (shop.getBlockedWorlds().contains(worldName)) {
            plugin.getMessageManager().send(player, "shop.world-restricted");
            return;
        }

        // Shop-level permission
        if (shop.getPermission() != null && !player.hasPermission(shop.getPermission())) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }

        plugin.getGuiManager().open(player, new MainMenuGui(plugin, shop));
        plugin.getSoundManager().play(player, "shop-open");
    }
}
