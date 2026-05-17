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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

        List<Shop> allShops = plugin.getShopManager().getAllShops().stream()
            .filter(Shop::isEnabled)
            .sorted(Comparator.comparingInt(s -> s.getDisplaySlot() >= 0 ? s.getDisplaySlot() : Integer.MAX_VALUE))
            .collect(Collectors.toList());

        if (allShops.isEmpty()) {
            plugin.getMessageManager().send(player, "shop.shop-not-found", "shop", "main");
            return true;
        }

        if (args.length == 0) {
            // Open the all-shops main menu
            plugin.getGuiManager().open(player, new MainMenuGui(plugin, allShops));
        } else {
            // Try to jump directly to a shop or category: /shop <shopid> or /shop <shopid> <categoryid>
            String shopId = args[0].toLowerCase();
            Shop target = plugin.getShopManager().getShopOrNull(shopId);

            // Fallback: search all shops for a matching category id
            if (target == null) {
                for (Shop s : allShops) {
                    ShopCategory cat = findCategory(s, shopId);
                    if (cat != null) {
                        if (cat.getPermission() != null && !player.hasPermission(cat.getPermission())) {
                            plugin.getMessageManager().send(player, "shop.no-access");
                            return true;
                        }
                        plugin.getGuiManager().open(player, new CategoryGui(plugin, s, cat, 0));
                        return true;
                    }
                }
                plugin.getMessageManager().send(player, "shop.section-not-found", "section", shopId);
                return true;
            }

            if (args.length >= 2) {
                ShopCategory cat = findCategory(target, args[1].toLowerCase());
                if (cat == null) {
                    plugin.getMessageManager().send(player, "shop.section-not-found", "section", args[1]);
                    return true;
                }
                if (cat.getPermission() != null && !player.hasPermission(cat.getPermission())) {
                    plugin.getMessageManager().send(player, "shop.no-access");
                    return true;
                }
                plugin.getGuiManager().open(player, new CategoryGui(plugin, target, cat, 0));
            } else {
                plugin.getGuiManager().open(player, new MainMenuGui(plugin, target));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            // Suggest shop ids
            for (Shop s : plugin.getShopManager().getAllShops()) {
                if (s.isEnabled() && s.getId().startsWith(prefix)) completions.add(s.getId());
            }
            // Also suggest category ids across all shops
            for (Shop s : plugin.getShopManager().getAllShops()) {
                for (ShopCategory cat : s.getCategories()) {
                    if (!cat.isHidden() && cat.getId().startsWith(prefix)
                            && !completions.contains(cat.getId())) {
                        completions.add(cat.getId());
                    }
                }
            }
        } else if (args.length == 2) {
            Shop target = plugin.getShopManager().getShopOrNull(args[0].toLowerCase());
            if (target != null) {
                String prefix = args[1].toLowerCase();
                for (ShopCategory cat : target.getCategories()) {
                    if (!cat.isHidden() && cat.getId().startsWith(prefix)) completions.add(cat.getId());
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
