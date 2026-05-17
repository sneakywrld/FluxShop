package dev.fluxshop.command;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.Shop;
import dev.fluxshop.model.ShopCategory;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.shop.ShopStandManager;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /fluxshop — Admin command hub.
 *
 * <p>Sub-commands: reload, editor, give, setprice, setmodifier, restock,
 * analytics, stand, import, export
 */
public class FluxShopAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of(
        "reload", "editor", "give", "setprice", "setmodifier", "restock", "analytics", "stand", "import", "export"
    );

    private final FluxShopPlugin plugin;

    public FluxShopAdminCommand(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("fluxshop.admin")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        long start = System.currentTimeMillis();
        switch (args[0].toLowerCase()) {

            case "reload" -> {
                if (!sender.hasPermission("fluxshop.admin.reload")) {
                    plugin.getMessageManager().send(sender, "general.no-permission"); return true;
                }
                plugin.reload();
                long ms = System.currentTimeMillis() - start;
                plugin.getMessageManager().send(sender, "general.reload-success", "ms", ms);
            }

            case "editor" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessageManager().send(sender, "general.player-only"); return true;
                }
                if (!sender.hasPermission("fluxshop.admin.editor")) {
                    plugin.getMessageManager().send(sender, "general.no-permission"); return true;
                }
                plugin.getGuiManager().open(player, new dev.fluxshop.gui.AdminShopGui(plugin));
            }

            case "give" -> {
                if (!sender.hasPermission("fluxshop.admin.give")) {
                    plugin.getMessageManager().send(sender, "general.no-permission"); return true;
                }
                if (args.length < 4) {
                    plugin.getMessageManager().sendRaw(sender, "&cUsage: /fluxshop give <player> <shopid> <itemid> [amount]");
                    return true;
                }
                org.bukkit.entity.Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    plugin.getMessageManager().send(sender, "general.player-not-found", "player", args[1]);
                    return true;
                }
                String shopId  = args[2];
                String itemId  = args[3];
                int    amount  = args.length > 4 ? parseInt(args[4], 1) : 1;
                plugin.getShopManager().findItem(shopId, "*", itemId).ifPresentOrElse(
                    shopItem -> {
                        org.bukkit.inventory.ItemStack give = shopItem.getDisplayItem().clone();
                        give.setAmount(amount);
                        target.getInventory().addItem(give);
                        plugin.getMessageManager().send(sender, "admin.given-item",
                            "player", target.getName(), "amount", amount, "item", itemId);
                    },
                    () -> plugin.getMessageManager().send(sender, "admin.shop-not-found", "shop", shopId + "/" + itemId)
                );
            }

            case "setprice" -> {
                if (!sender.hasPermission("fluxshop.admin.setprice")) {
                    plugin.getMessageManager().send(sender, "general.no-permission"); return true;
                }
                // /fluxshop setprice <shopid> <itemid> <buy|sell> <price>
                if (args.length < 5) { plugin.getMessageManager().sendRaw(sender, "&cUsage: /fluxshop setprice <shopid> <itemid> <buy|sell> <price>"); return true; }
                if (!args[3].equalsIgnoreCase("buy") && !args[3].equalsIgnoreCase("sell")) {
                    plugin.getMessageManager().sendRaw(sender, "&cType must be 'buy' or 'sell'.");
                    return true;
                }
                double price = parseDouble(args[4], -1);
                if (price < 0) { plugin.getMessageManager().send(sender, "general.invalid-number", "input", args[4]); return true; }
                boolean updated = plugin.getShopManager().updateItemPrice(args[1], args[2], args[3], price);
                if (updated) {
                    plugin.getMessageManager().send(sender, "admin.price-updated",
                        "item", args[1] + "/" + args[2], "type", args[3], "price", price);
                } else {
                    plugin.getMessageManager().send(sender, "admin.shop-not-found", "shop", args[1] + "/" + args[2]);
                }
            }

            case "setmodifier" -> {
                if (!sender.hasPermission("fluxshop.admin.setmodifier")) {
                    plugin.getMessageManager().send(sender, "general.no-permission"); return true;
                }
                // /fluxshop setmodifier <player> <itemid> <multiplier> [durationSeconds]
                if (args.length < 4) { plugin.getMessageManager().sendRaw(sender, "&cUsage: /fluxshop setmodifier <player> <itemid> <multiplier> [durationSeconds]"); return true; }
                org.bukkit.entity.Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { plugin.getMessageManager().send(sender, "general.player-not-found", "player", args[1]); return true; }
                double mult = parseDouble(args[3], -1);
                if (mult <= 0) { plugin.getMessageManager().sendRaw(sender, "&cMultiplier must be a positive number (e.g. 0.9 for 10% off, 1.2 for 20% markup)."); return true; }
                long durationMs = 0;
                if (args.length > 4) {
                    long secs = parseLong(args[4], 0);
                    durationMs = secs * 1000L;
                }
                plugin.getPriceModifierRepository().setModifier(target.getUniqueId(), args[2], mult, durationMs);
                String durLabel = durationMs > 0 ? " for " + args[4] + "s" : " permanently";
                plugin.getMessageManager().sendRaw(sender, "&e[FluxShop] &aSet price modifier ×" + mult + " on &f" + args[2]
                    + " &afor &f" + target.getName() + durLabel + ".");
                plugin.getMessageManager().sendRaw(target, "&e[FluxShop] &7Your price modifier for &f" + args[2] + " &7has been set to &a×" + mult + durLabel + ".");
            }

            case "restock" -> {
                if (!sender.hasPermission("fluxshop.admin.restock")) {
                    plugin.getMessageManager().send(sender, "general.no-permission"); return true;
                }
                if (args.length < 2) { plugin.getMessageManager().sendRaw(sender, "&cUsage: /fluxshop restock <shopid> [itemid]"); return true; }
                String shopId = args[1];
                String itemId = args.length > 2 ? args[2] : null;
                dev.fluxshop.shop.StockManager stockMgr = new dev.fluxshop.shop.StockManager(plugin);
                if (itemId != null) {
                    stockMgr.restock(shopId, itemId);
                    plugin.getMessageManager().send(sender, "admin.restocked", "shop", shopId, "item", " → " + itemId);
                } else {
                    stockMgr.restockAll(shopId);
                    plugin.getMessageManager().send(sender, "admin.restocked", "shop", shopId, "item", "");
                }
            }

            case "analytics" -> {
                if (!sender.hasPermission("fluxshop.admin.analytics")) {
                    plugin.getMessageManager().send(sender, "general.no-permission"); return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("export")) {
                    // Console-friendly CSV export
                    exportAnalyticsCsv(sender);
                } else {
                    if (!(sender instanceof Player player)) { plugin.getMessageManager().send(sender, "general.player-only"); return true; }
                    plugin.getGuiManager().open(player, new dev.fluxshop.gui.AnalyticsGui(plugin));
                }
            }

            case "stand" -> {
                if (!sender.hasPermission("fluxshop.admin.stand")) {
                    plugin.getMessageManager().send(sender, "general.no-permission"); return true;
                }
                if (!(sender instanceof Player player)) {
                    plugin.getMessageManager().send(sender, "general.player-only"); return true;
                }
                handleStand(player, args);
            }

            case "import" -> {
                if (!sender.hasPermission("fluxshop.admin.import")) {
                    plugin.getMessageManager().send(sender, "general.no-permission"); return true;
                }
                if (args.length < 2) {
                    plugin.getMessageManager().sendRaw(sender, "&cUsage: /fluxshop import <essentials>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("essentials")) {
                    importFromEssentials(sender);
                } else {
                    plugin.getMessageManager().sendRaw(sender, "&cUnknown import source. Available: &fessentials");
                }
            }

            case "export" -> {
                if (!sender.hasPermission("fluxshop.admin.export")) {
                    plugin.getMessageManager().send(sender, "general.no-permission"); return true;
                }
                exportShops(sender);
            }

            default -> sendUsage(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return SUB_COMMANDS.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stand")) {
            return List.of("set", "remove", "list");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            return List.of("essentials");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("stand") && args[1].equalsIgnoreCase("set")) {
            // Suggest shop ids
            return plugin.getShopManager().getAllShops().stream()
                .map(s -> s.getId())
                .filter(id -> id.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        return List.of();
    }

    // ── Essentials import ─────────────────────────────────────────────────────

    /**
     * Reads Essentials' {@code worth.yml} and generates a FluxShop
     * {@code shops/imported_essentials.yml} file using those sell prices.
     * Buy price is set to 2× the worth value; sell price equals worth.
     * Items that already exist in a loaded shop are skipped to avoid duplicates.
     */
    private void importFromEssentials(CommandSender sender) {
        File worthFile = new File(plugin.getServer().getWorldContainer(), "plugins/Essentials/worth.yml");
        if (!worthFile.exists()) {
            plugin.getMessageManager().sendRaw(sender, "&c[Import] Could not find plugins/Essentials/worth.yml");
            return;
        }

        YamlConfiguration worth = YamlConfiguration.loadConfiguration(worthFile);
        var pricesSection = worth.getConfigurationSection("worth");
        if (pricesSection == null) {
            plugin.getMessageManager().sendRaw(sender, "&c[Import] worth.yml has no 'worth' section.");
            return;
        }

        // Build output YAML manually for clean formatting
        StringBuilder sb = new StringBuilder();
        sb.append("id: imported_essentials\n");
        sb.append("display-name: \"&6Essentials Import\"\n");
        sb.append("enabled: true\n");
        sb.append("display-slot: 99\n");
        sb.append("display-item:\n");
        sb.append("  material: CHEST\n");
        sb.append("  name: \"&6Essentials Import\"\n");
        sb.append("categories:\n");
        sb.append("  imported:\n");
        sb.append("    display-name: \"&6Imported Items\"\n");
        sb.append("    enabled: true\n");
        sb.append("    icon:\n");
        sb.append("      material: CHEST\n");
        sb.append("      name: \"&6Imported from Essentials\"\n");
        sb.append("    items:\n");

        int count = 0;
        int slot  = 0;
        for (String key : pricesSection.getKeys(false)) {
            double sellPrice = pricesSection.getDouble(key, -1);
            if (sellPrice <= 0) continue;

            String material = key.toUpperCase().replace("-", "_").replace(" ", "_");
            String itemId   = material.toLowerCase();
            double buyPrice = sellPrice * 2.0;

            sb.append("      ").append(itemId).append(":\n");
            sb.append("        material: ").append(material).append("\n");
            sb.append("        buy-price: ").append(String.format("%.2f", buyPrice)).append("\n");
            sb.append("        sell-price: ").append(String.format("%.2f", sellPrice)).append("\n");
            sb.append("        slot: ").append(slot++).append("\n");
            count++;
        }

        if (count == 0) {
            plugin.getMessageManager().sendRaw(sender, "&c[Import] No valid price entries found in worth.yml.");
            return;
        }

        try {
            File shopsDir = new File(plugin.getDataFolder(), "shops");
            shopsDir.mkdirs();
            File outFile = new File(shopsDir, "imported_essentials.yml");
            try (PrintWriter pw = new PrintWriter(outFile)) {
                pw.print(sb);
            }
            plugin.getMessageManager().sendRaw(sender, "&a[Import] &7Imported &f" + count + " &7items → &fshops/imported_essentials.yml");
            plugin.getMessageManager().sendRaw(sender, "&7Run &f/fluxshop reload &7to load the new shop.");
        } catch (IOException e) {
            plugin.getMessageManager().sendRaw(sender, "&c[Import] Failed to write output file: " + e.getMessage());
        }
    }

    // ── Shop export ───────────────────────────────────────────────────────────

    /**
     * Exports all loaded shops as YAML files to {@code exports/shops/<timestamp>/}.
     * Each shop gets its own file, preserving the same format that FluxShop reads.
     */
    private void exportShops(CommandSender sender) {
        String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date());
        File dir  = new File(plugin.getDataFolder(), "exports/shops/" + ts);
        dir.mkdirs();

        int shopCount = 0;
        for (Shop shop : plugin.getShopManager().getAllShops()) {
            try {
                File outFile = new File(dir, shop.getId() + ".yml");
                exportShopToFile(shop, outFile);
                shopCount++;
            } catch (IOException e) {
                plugin.getMessageManager().sendRaw(sender, "&c[Export] Failed to export shop &f" + shop.getId() + "&c: " + e.getMessage());
            }
        }

        plugin.getMessageManager().sendRaw(sender, "&a[Export] &7Exported &f" + shopCount
            + " &7shops to &fplugins/FluxShop/exports/shops/" + ts + "/");
    }

    private void exportShopToFile(Shop shop, File outFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("id: ").append(shop.getId()).append("\n");
        sb.append("display-name: \"").append(shop.getDisplayName()).append("\"\n");
        sb.append("enabled: ").append(shop.isEnabled()).append("\n");
        if (shop.getPermission() != null && !shop.getPermission().isEmpty()) {
            sb.append("permission: ").append(shop.getPermission()).append("\n");
        }
        sb.append("categories:\n");

        for (ShopCategory cat : shop.getCategories()) {
            sb.append("  ").append(cat.getId()).append(":\n");
            sb.append("    display-name: \"").append(cat.getDisplayName()).append("\"\n");
            sb.append("    enabled: true\n");
            if (cat.getIcon() != null) {
                sb.append("    icon:\n");
                sb.append("      material: ").append(cat.getIcon().getType().name()).append("\n");
            }
            sb.append("    items:\n");

            for (ShopItem item : cat.getItems()) {
                sb.append("      ").append(item.getId()).append(":\n");
                if (item.getDisplayItem() != null) {
                    sb.append("        material: ").append(item.getDisplayItem().getType().name()).append("\n");
                }
                if (item.getCustomName() != null) {
                    sb.append("        name: \"").append(item.getCustomName()).append("\"\n");
                }
                if (item.getBuyPrice() >= 0) {
                    sb.append("        buy-price: ").append(item.getBuyPrice()).append("\n");
                }
                if (item.getSellPrice() >= 0) {
                    sb.append("        sell-price: ").append(item.getSellPrice()).append("\n");
                }
                if (item.getGlobalStock() >= 0) {
                    sb.append("        global-stock: ").append(item.getGlobalStock()).append("\n");
                }
                if (item.getPlayerLimit() >= 0) {
                    sb.append("        player-limit: ").append(item.getPlayerLimit()).append("\n");
                }
                if (item.getBuyPermission() != null && !item.getBuyPermission().isEmpty()) {
                    sb.append("        buy-permission: ").append(item.getBuyPermission()).append("\n");
                }
                if (item.getSellPermission() != null && !item.getSellPermission().isEmpty()) {
                    sb.append("        sell-permission: ").append(item.getSellPermission()).append("\n");
                }
                if (item.getGuiSlot() >= 0) {
                    sb.append("        slot: ").append(item.getGuiSlot()).append("\n");
                }
                if (item.getMythicItemId() != null) {
                    sb.append("        mythic-item: ").append(item.getMythicItemId()).append("\n");
                }
                if (item.getItemsAdderId() != null) {
                    sb.append("        items-adder: ").append(item.getItemsAdderId()).append("\n");
                }
                if (item.getOraxenId() != null) {
                    sb.append("        oraxen: ").append(item.getOraxenId()).append("\n");
                }
                if (!item.getOnBuyCommands().isEmpty()) {
                    sb.append("        on-buy-commands:\n");
                    item.getOnBuyCommands().forEach(c -> sb.append("          - \"").append(c).append("\"\n"));
                }
                if (!item.getOnSellCommands().isEmpty()) {
                    sb.append("        on-sell-commands:\n");
                    item.getOnSellCommands().forEach(c -> sb.append("          - \"").append(c).append("\"\n"));
                }
            }
        }

        try (PrintWriter pw = new PrintWriter(outFile)) {
            pw.print(sb);
        }
    }

    // ── Stand management ──────────────────────────────────────────────────────

    private void handleStand(Player player, String[] args) {
        ShopStandManager standManager = plugin.getShopStandManager();
        String sub = args.length > 1 ? args[1].toLowerCase() : "help";

        switch (sub) {
            case "set" -> {
                if (args.length < 3) {
                    plugin.getMessageManager().sendRaw(player, "&cUsage: /fluxshop stand set <shopId>");
                    return;
                }
                String shopId = args[2];
                if (plugin.getShopManager().getShop(shopId).isEmpty()) {
                    plugin.getMessageManager().send(player, "admin.shop-not-found", "shop", shopId);
                    return;
                }
                Block target = player.getTargetBlockExact(5);
                if (target == null) {
                    plugin.getMessageManager().sendRaw(player, "&cYou must be looking at a block within 5 blocks.");
                    return;
                }
                standManager.addStand(target.getLocation(), shopId);
                plugin.getMessageManager().sendRaw(player, "&a[FluxShop] &7Block at &f" + fmtLoc(target)
                    + " &7registered as stand for shop &f" + shopId + "&7.");
            }
            case "remove" -> {
                Block target = player.getTargetBlockExact(5);
                if (target == null) {
                    plugin.getMessageManager().sendRaw(player, "&cYou must be looking at a block within 5 blocks.");
                    return;
                }
                boolean removed = standManager.removeStand(target.getLocation());
                if (removed) {
                    plugin.getMessageManager().sendRaw(player, "&a[FluxShop] &7Shop stand removed from " + fmtLoc(target) + ".");
                } else {
                    plugin.getMessageManager().sendRaw(player, "&c[FluxShop] &7That block is not a registered shop stand.");
                }
            }
            case "list" -> {
                if (standManager.size() == 0) {
                    plugin.getMessageManager().sendRaw(player, "&e[FluxShop] &7No shop stands registered.");
                    return;
                }
                plugin.getMessageManager().sendRaw(player, "&6[FluxShop] &7Registered stands (" + standManager.size() + "):");
                for (var entry : standManager.allStands()) {
                    plugin.getMessageManager().sendRaw(player, "&7  " + entry.getKey() + " &6\u2192 &f" + entry.getValue());
                }
            }
            default -> {
                plugin.getMessageManager().sendRaw(player, "&cUsage: /fluxshop stand <set <shopId>|remove|list>");
            }
        }
    }

    private String fmtLoc(Block b) {
        return b.getWorld().getName() + " " + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private void sendUsage(CommandSender sender) {
        plugin.getMessageManager().sendRaw(sender, "&6FluxShop Admin Commands:");
        SUB_COMMANDS.forEach(sub -> plugin.getMessageManager().sendRaw(sender, "&7  /fluxshop " + sub));
    }

    private void exportAnalyticsCsv(CommandSender sender) {
        plugin.getMessageManager().sendRaw(sender, "&e[Analytics] &7Exporting, please wait...");
        dev.fluxshop.storage.AnalyticsRepository repo = plugin.getAnalyticsRepository();
        repo.getTopBought(0, 100).thenAccept(bought ->
        repo.getTopSold(0, 100).thenAccept(sold ->
        repo.getTotalRevenue(0).thenAccept(revenue ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    // Futures return null on DB error — use safe defaults
                    java.util.List<dev.fluxshop.storage.AnalyticsRepository.ItemStat> safeBought =
                        bought  != null ? bought  : java.util.List.of();
                    java.util.List<dev.fluxshop.storage.AnalyticsRepository.ItemStat> safeSold =
                        sold    != null ? sold    : java.util.List.of();
                    java.util.Map<String, Double> safeRevenue =
                        revenue != null ? revenue : java.util.Map.of();

                    java.io.File dir = new java.io.File(plugin.getDataFolder(), "exports");
                    dir.mkdirs();
                    String ts = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new java.util.Date());
                    java.io.File file = new java.io.File(dir, "analytics_" + ts + ".csv");
                    try (java.io.PrintWriter pw = new java.io.PrintWriter(file)) {
                        pw.println("# FluxShop Analytics Export — " + ts);
                        pw.println("# TOP BOUGHT ITEMS");
                        pw.println("item_id,item_name,quantity,revenue");
                        for (var s : safeBought) pw.printf("%s,%s,%d,%.2f%n",
                            s.itemId(), s.itemName() != null ? s.itemName() : "", s.quantity(), s.revenue());
                        pw.println("# TOP SOLD ITEMS");
                        pw.println("item_id,item_name,quantity,payout");
                        for (var s : safeSold) pw.printf("%s,%s,%d,%.2f%n",
                            s.itemId(), s.itemName() != null ? s.itemName() : "", s.quantity(), s.revenue());
                        pw.println("# REVENUE BY CURRENCY");
                        pw.println("currency,total");
                        for (var e : safeRevenue.entrySet()) pw.printf("%s,%.2f%n", e.getKey(), e.getValue());
                    }
                    plugin.getMessageManager().sendRaw(sender, "&a[Analytics] Exported: plugins/FluxShop/exports/" + file.getName());
                } catch (Exception e) {
                    plugin.getMessageManager().sendRaw(sender, "&c[Analytics] Export failed: " + e.getMessage());
                }
            })
        )));
    }

    private int    parseInt(String s, int def)    { try { return Integer.parseInt(s); }    catch (Exception e) { return def; } }
    private double parseDouble(String s, double d){ try { return Double.parseDouble(s); }  catch (Exception e) { return d; } }
    private long   parseLong(String s, long def)  { try { return Long.parseLong(s); }     catch (Exception e) { return def; } }
}
