package dev.fluxshop.command;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.gui.MainMenuGui;
import dev.fluxshop.gui.CategoryGui;
import dev.fluxshop.model.Shop;
import dev.fluxshop.model.ShopCategory;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /shop [section] — Open the shop or jump to a section.
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private final FluxShopPlugin plugin;

    public ShopCommand(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("fluxshop.shop")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return true;
        }

        // World check
        List<String> disabled = plugin.getConfigManager().getStringList("general.disabled-worlds");
        if (disabled.contains(player.getWorld().getName())) {
            plugin.getMessageManager().send(player, "general.world-disabled");
            return true;
        }

        // Gamemode check
        List<String> banned = plugin.getConfigManager().getStringList("general.banned-gamemodes");
        if (banned.contains(player.getGameMode().name())) {
            plugin.getMessageManager().send(player, "general.gamemode-blocked");
            return true;
        }

        // WorldGuard region check
        if (!plugin.getCompatManager().isShopAllowed(player)) {
            plugin.getMessageManager().send(player, "shop.world-restricted");
            return true;
        }

        Shop shop = plugin.getShopManager().getDefaultShop();
        if (shop == null) {
            plugin.getMessageManager().send(player, "shop.shop-not-found", "shop", "main");
            return true;
        }

        if (args.length == 0) {
            // Open main menu
            plugin.getGuiManager().open(player, new MainMenuGui(plugin, shop));
        } else {
            // Try to jump directly to a category
            String sectionId = args[0].toLowerCase();
            ShopCategory cat = findCategory(shop, sectionId);
            if (cat == null) {
                plugin.getMessageManager().send(player, "shop.section-not-found", "section", sectionId);
                return true;
            }
            if (cat.getPermission() != null && !player.hasPermission(cat.getPermission())) {
                plugin.getMessageManager().send(player, "shop.no-access");
                return true;
            }
            plugin.getGuiManager().open(player, new CategoryGui(plugin, shop, cat, 0));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            Shop shop = plugin.getShopManager().getDefaultShop();
            if (shop != null) {
                for (ShopCategory cat : shop.getCategories()) {
                    if (!cat.isHidden() && cat.getId().startsWith(args[0].toLowerCase())) {
                        completions.add(cat.getId());
                    }
                }
            }
        }
        return completions;
    }

    private ShopCategory findCategory(Shop shop, String id) {
        return shop.getCategories().stream()
            .filter(c -> c.getId().equalsIgnoreCase(id))
            .findFirst().orElse(null);
    }
}
