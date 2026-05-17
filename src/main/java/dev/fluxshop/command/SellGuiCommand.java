package dev.fluxshop.command;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.gui.SellDropGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /sellgui — Open the drag-and-drop sell interface.
 */
public class SellGuiCommand implements CommandExecutor {

    private final FluxShopPlugin plugin;

    public SellGuiCommand(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("fluxshop.sellgui")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return true;
        }
        if (!plugin.getConfigManager().getBoolean("shop.sellgui-enabled", true)) {
            plugin.getMessageManager().send(player, "general.feature-disabled");
            return true;
        }
        if (plugin.getCompatManager().isBedrockPlayer(player)
                && plugin.getConfigManager().getBoolean("bedrock.disable-sellgui", true)) {
            plugin.getMessageManager().sendRaw(player, "&cThe SellGUI is not available for Bedrock players.");
            return true;
        }
        plugin.getGuiManager().open(player, new SellDropGui(plugin));
        return true;
    }
}
