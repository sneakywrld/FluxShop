package dev.fluxshop.command;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /trade <player|accept|cancel> — Send, accept, or cancel a player trade.
 */
public class TradeCommand implements CommandExecutor, TabCompleter {

    private final FluxShopPlugin plugin;

    public TradeCommand(FluxShopPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("fluxshop.trade")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return true;
        }
        if (!plugin.getConfigManager().getBoolean("trade.enabled", true)) {
            plugin.getMessageManager().send(player, "general.feature-disabled");
            return true;
        }

        if (args.length == 0) {
            plugin.getMessageManager().sendRaw(player, "&cUsage: /trade <player|accept|cancel>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "accept" -> {
                String err = plugin.getTradeManager().acceptRequest(player);
                if (err != null) plugin.getMessageManager().sendRaw(player, "&c" + err);
            }
            case "cancel" -> plugin.getTradeManager().cancel(player);
            default -> {
                Player target = plugin.getServer().getPlayer(args[0]);
                if (target == null || !target.isOnline()) {
                    plugin.getMessageManager().send(player, "general.player-not-found", "player", args[0]);
                    return true;
                }
                String err = plugin.getTradeManager().sendRequest(player, target);
                if (err != null) plugin.getMessageManager().sendRaw(player, "&c" + err);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return List.of("accept", "cancel");
        return List.of();
    }
}
