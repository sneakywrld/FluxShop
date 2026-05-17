package dev.fluxshop.command;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /blackmarket — Open the Black Market GUI.
 */
public class BlackMarketCommand implements CommandExecutor {

    private final FluxShopPlugin plugin;

    public BlackMarketCommand(FluxShopPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { plugin.getMessageManager().send(sender, "general.player-only"); return true; }
        if (!player.hasPermission("fluxshop.blackmarket")) { plugin.getMessageManager().send(player, "general.no-permission"); return true; }
        if (!plugin.getConfigManager().getBoolean("black-market.enabled", true)) { plugin.getMessageManager().send(player, "general.feature-disabled"); return true; }

        plugin.getGuiManager().open(player, new dev.fluxshop.gui.BlackMarketGui(plugin));
        return true;
    }
}
