package dev.fluxshop.compat.citizens;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.gui.MainMenuGui;
import dev.fluxshop.model.Shop;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import java.util.Optional;

/**
 * Citizens NPC trait that opens an assigned FluxShop when right-clicked.
 *
 * <p>Assign to an NPC via: {@code /trait fluxshop}
 * Set the shop via: {@code /npc shop <shopid>}  (handled by {@link ShopNPCCommand})
 *
 * <p>Configuration is stored in Citizens' NPC data file under the key {@code fluxshop-id}.
 */
@TraitName("fluxshop")
public class ShopNPCTrait extends Trait {

    private static FluxShopPlugin plugin;

    /** Called by {@link ShopNPCCommand} once the plugin is ready. */
    public static void init(FluxShopPlugin p) { plugin = p; }

    /** The shop ID this NPC opens. Persisted in Citizens' NPC data. */
    private String shopId;

    public ShopNPCTrait() {
        super("fluxshop");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void load(net.citizensnpcs.api.util.DataKey key) {
        shopId = key.getString("shop-id", null);
    }

    @Override
    public void save(net.citizensnpcs.api.util.DataKey key) {
        if (shopId != null) key.setString("shop-id", shopId);
    }

    // ── Right-click handler ───────────────────────────────────────────────────

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        if (!event.getNPC().equals(getNPC())) return;
        Player player = event.getClicker();

        if (plugin == null) {
            player.sendMessage("§cFluxShop is not available."); // plugin unavailable — no MessageManager
            return;
        }
        if (!player.hasPermission("fluxshop.shop")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }

        if (shopId == null || shopId.isEmpty()) {
            plugin.getMessageManager().sendRaw(player, "&cThis NPC has no shop assigned. &7(Admin: &f/npc fluxshop <shopid>&7)");
            return;
        }

        Optional<Shop> shopOpt = plugin.getShopManager().getShop(shopId);
        if (shopOpt.isEmpty()) {
            plugin.getMessageManager().sendRaw(player, "&cShop &f'" + shopId + "' &cnot found.");
            return;
        }

        Shop shop = shopOpt.get();

        // Check world restrictions
        String worldName = player.getWorld().getName();
        if (!shop.getAllowedWorlds().isEmpty() && !shop.getAllowedWorlds().contains(worldName)) {
            plugin.getMessageManager().send(player, "shop.world-restricted");
            return;
        }
        if (shop.getBlockedWorlds().contains(worldName)) {
            plugin.getMessageManager().send(player, "shop.world-restricted");
            return;
        }

        // Check shop-level permission
        if (shop.getPermission() != null && !player.hasPermission(shop.getPermission())) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }

        plugin.getGuiManager().open(player, new MainMenuGui(plugin, shop));
        plugin.getSoundManager().play(player, "shop-open");
    }

    // ── Shop assignment ───────────────────────────────────────────────────────

    public String getShopId() { return shopId; }

    public void setShopId(String id) { this.shopId = id; }
}
