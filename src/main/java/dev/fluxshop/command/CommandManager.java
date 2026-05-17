package dev.fluxshop.command;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.command.PluginCommand;

/**
 * Registers all FluxShop commands with the server.
 */
public class CommandManager {

    private final FluxShopPlugin plugin;

    public CommandManager(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        register("shop",       new ShopCommand(plugin));
        register("sellall",    new SellAllCommand(plugin));
        register("sellgui",    new SellGuiCommand(plugin));
        register("auction",    new AuctionCommand(plugin));
        register("trade",      new TradeCommand(plugin));
        register("blackmarket",new BlackMarketCommand(plugin));
        register("fluxshop",   new FluxShopAdminCommand(plugin));
    }

    private void register(String name, Object executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.getLogger().warning("Command /" + name + " not found in plugin.yml!");
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ce)  cmd.setExecutor(ce);
        if (executor instanceof org.bukkit.command.TabCompleter    tc)  cmd.setTabCompleter(tc);
    }
}
