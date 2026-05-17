package dev.fluxshop.listener;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.gui.MainMenuGui;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Handles right-clicking physical shop stands (blocks in the world registered
 * via {@code /fluxshop stand set <shopId>}).
 *
 * <p>Stand locations are persisted to {@code stands.yml} and managed by
 * {@link dev.fluxshop.shop.ShopStandManager}.  When a player right-clicks a
 * registered block the associated shop GUI opens.  When a registered block is
 * broken by a player with the stand-admin permission the stand record is
 * automatically removed so the data file never goes stale.
 *
 * <p>NPC shops (Citizens) are handled separately via {@code ShopNPCTrait}.
 */
public class ShopStandListener implements Listener {

    private final FluxShopPlugin plugin;

    public ShopStandListener(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Right-click → open shop ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        String shopId = plugin.getShopStandManager().getShopId(block.getLocation());
        if (shopId == null) return;  // not a registered stand

        event.setCancelled(true);

        Player player = event.getPlayer();

        plugin.getShopManager().getShop(shopId).ifPresentOrElse(
            shop -> {
                if (!shop.isEnabled()) {
                    plugin.getMessageManager().send(player, "general.feature-disabled");
                    return;
                }
                if (shop.getPermission() != null && !shop.getPermission().isEmpty() && !player.hasPermission(shop.getPermission())) {
                    plugin.getMessageManager().send(player, "general.no-permission");
                    return;
                }
                plugin.getGuiManager().open(player, new MainMenuGui(plugin, shop));
            },
            () -> plugin.getLogger().warning("[ShopStand] Block at " + block.getLocation()
                + " references unknown shop '" + shopId + "' — consider running /fluxshop stand remove")
        );
    }

    // ── Block break → auto-remove stand record ────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!plugin.getShopStandManager().isStand(block.getLocation())) return;

        // Only players with stand-admin permission can break registered stands.
        // If they don't have permission, cancel the break to protect the stand.
        Player player = event.getPlayer();
        if (!player.hasPermission("fluxshop.admin.stand")) {
            event.setCancelled(true);
            plugin.getMessageManager().send(player, "stand.protected");
            return;
        }

        // Admin is breaking a stand — remove the record automatically
        plugin.getShopStandManager().removeStand(block.getLocation());
        plugin.getMessageManager().send(player, "stand.removed-on-break");
    }
}
