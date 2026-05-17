package dev.fluxshop.command;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.gui.SellAllGui;
import dev.fluxshop.shop.SellAllProcessor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /sellall [hand|<material>] — Sell all sellable items in inventory.
 */
public class SellAllCommand implements CommandExecutor, TabCompleter {

    private final FluxShopPlugin plugin;

    public SellAllCommand(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("fluxshop.sellall")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return true;
        }
        if (!plugin.getConfigManager().getBoolean("shop.sellall-enabled", true)) {
            plugin.getMessageManager().send(player, "general.feature-disabled");
            return true;
        }

        // No arguments → open the GUI preview
        if (args.length == 0) {
            plugin.getGuiManager().open(player, new SellAllGui(plugin));
            return true;
        }

        Material filter = null;
        if (args[0].equalsIgnoreCase("hand")) {
            if (player.getInventory().getItemInMainHand().getType().isAir()) {
                plugin.getMessageManager().sendRaw(player, "&cYou're not holding anything.");
                return true;
            }
            filter = player.getInventory().getItemInMainHand().getType();
        } else {
            try {
                filter = Material.valueOf(args[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getMessageManager().sendRaw(player, "&cUnknown material: &f" + args[0]);
                return true;
            }
        }

        // Direct sell with material filter (no GUI needed)
        final Material finalFilter = filter;
        new SellAllProcessor(plugin).sellAll(player, finalFilter);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("hand");
            for (Material m : Material.values()) {
                if (!m.isAir() && m.isItem() && m.name().startsWith(args[0].toUpperCase())) {
                    completions.add(m.name().toLowerCase());
                }
            }
        }
        return completions;
    }
}
