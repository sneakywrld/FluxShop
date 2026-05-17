package dev.fluxshop.command;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.gui.AuctionCreateGui;
import dev.fluxshop.gui.AuctionHouseGui;
import dev.fluxshop.gui.MyListingsGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * /auction — Open the Auction House or perform auction sub-actions.
 *
 * Sub-commands:
 *   /auction                — open browser
 *   /auction list           — list item in hand for auction
 *   /auction listings       — view your own listings
 *   /auction collect        — collect won items / expired listings
 *   /auction history        — view your auction history (opens listings GUI filtered)
 */
public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final FluxShopPlugin plugin;

    public AuctionCommand(FluxShopPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("fluxshop.auction")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return true;
        }
        if (!plugin.getConfigManager().getBoolean("auction.enabled", true)) {
            plugin.getMessageManager().send(player, "general.feature-disabled");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "browse";

        switch (sub) {
            case "collect" -> plugin.getAuctionService().collect(player);

            case "list" -> {
                if (!player.hasPermission("fluxshop.auction.list")) {
                    plugin.getMessageManager().send(player, "general.no-permission");
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    plugin.getMessageManager().sendRaw(player, "&cHold the item you want to auction in your main hand.");
                    return true;
                }
                plugin.getGuiManager().open(player, new AuctionCreateGui(plugin, hand.clone()));
            }

            case "listings", "history" ->
                plugin.getGuiManager().open(player, new MyListingsGui(plugin, player));

            default -> plugin.getGuiManager().open(player, new AuctionHouseGui(plugin, 0));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "listings", "collect", "history");
        return List.of();
    }
}
