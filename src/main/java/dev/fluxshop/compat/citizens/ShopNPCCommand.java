package dev.fluxshop.compat.citizens;

import dev.fluxshop.FluxShopPlugin;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitFactory;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Registers the FluxShop NPC trait and provides the {@code /npc fluxshop <shopid>} sub-command.
 *
 * <p>Called from {@link dev.fluxshop.compat.CompatManager} when Citizens is detected.
 */
public class ShopNPCCommand implements CommandExecutor {

    private final FluxShopPlugin plugin;

    public ShopNPCCommand(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the trait with Citizens' trait registry.
     */
    public void register() {
        ShopNPCTrait.init(plugin);
        try {
            TraitFactory factory = CitizensAPI.getTraitFactory();
            factory.registerTrait(TraitInfo.create(ShopNPCTrait.class).withName("fluxshop"));
            plugin.getLogger().info("Citizens NPC trait 'fluxshop' registered.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register Citizens NPC trait: " + e.getMessage());
        }
    }

    /**
     * Handles: /npc fluxshop <npcid> <shopid>
     *
     * <p>Example: {@code /npc fluxshop 3 survival} assigns shop "survival" to NPC #3.
     * Use {@code /npc list} to find NPC IDs.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only.");
            return true;
        }
        if (!player.hasPermission("fluxshop.admin.editor")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessageManager().sendRaw(player, "&cUsage: /npc fluxshop <npcid> <shopid>");
            return true;
        }

        // Parse NPC id
        int npcId;
        try {
            npcId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRaw(player, "&cInvalid NPC id '&f" + args[0] + "&c'. Use /npc list to find NPC ids.");
            return true;
        }

        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null) {
            plugin.getMessageManager().sendRaw(player, "&cNo NPC found with id &f" + npcId + "&c. Use &f/npc list&c to see all NPCs.");
            return true;
        }

        String shopId = args[1];
        if (plugin.getShopManager().getShop(shopId).isEmpty()) {
            plugin.getMessageManager().sendRaw(player, "&cShop '&f" + shopId + "&c' not found.");
            return true;
        }

        // Add/update the trait
        if (!npc.hasTrait(ShopNPCTrait.class)) {
            npc.addTrait(ShopNPCTrait.class);
        }
        npc.getOrAddTrait(ShopNPCTrait.class).setShopId(shopId);
        plugin.getMessageManager().sendRaw(player, "&a[FluxShop] &fNPC &e#" + npcId + " &f('" + npc.getName() + "') will now open shop &e" + shopId + "&f.");
        return true;
    }
}
