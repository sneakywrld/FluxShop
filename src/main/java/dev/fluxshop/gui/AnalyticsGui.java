package dev.fluxshop.gui;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.AnalyticsSnapshot;
import dev.fluxshop.storage.AnalyticsRepository;
import dev.fluxshop.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Admin Analytics Dashboard GUI — wired to live AnalyticsRepository data.
 *
 * Layout (6 rows):
 *   Slot  4       — Economy Health Score
 *   Slots 9-13    — Top 5 bought items (24h)
 *   Slots 18-22   — Top 5 sold items (24h)
 *   Slots 27-35   — Revenue by currency (all-time)
 *   Slot 49       — Export CSV
 *   Slot 45       — Back
 */
public class AnalyticsGui extends FluxGui {

    private static final int  HEALTH_SLOT  = 4;
    private static final int  EXPORT_SLOT  = 49;
    private static final int  BACK_SLOT    = 45;
    private static final long ONE_DAY_MS   = 86_400_000L;
    private static final long SEVEN_DAYS_MS = 7 * ONE_DAY_MS;

    private final FluxShopPlugin plugin;
    private Inventory inventory;

    public AnalyticsGui(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void open(Player player) {
        String title = plugin.getMessageManager().colorize(
            plugin.getConfigManager().getString("guis.analytics.title", "&4Analytics Dashboard"));
        inventory = Bukkit.createInventory(new ShopHolder(null, null), 54, title);

        // Fill with border immediately, then load data async
        ItemStack border = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, border);

        inventory.setItem(HEALTH_SLOT, new ItemBuilder(Material.CLOCK)
            .name("&eLoading analytics...").build());
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW).name("&cBack").build());
        inventory.setItem(EXPORT_SLOT, new ItemBuilder(Material.WRITABLE_BOOK)
            .name("&eExport CSV").lore("&7Export analytics data to file").build());

        player.openInventory(inventory);

        // Load all data concurrently then populate on main thread
        plugin.getAnalyticsRepository().loadSnapshot(ONE_DAY_MS, 5)
            .thenAccept(snapshot ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    populate(player, snapshot);
                }));
    }

    private void populate(Player player, AnalyticsSnapshot snapshot) {
        double             health  = snapshot.economyHealth();
        List<AnalyticsRepository.ItemStat> bought  = snapshot.topBought();
        List<AnalyticsRepository.ItemStat> sold    = snapshot.topSold();
        Map<String, Double>               revenue  = snapshot.revenueByCurrency();
        long               txCount = snapshot.transactionCount();

        // ── Economy Health ────────────────────────────────────────────────────
        String healthColor = health >= 80 ? "&a" : health >= 50 ? "&e" : "&c";
        inventory.setItem(HEALTH_SLOT, new ItemBuilder(Material.EMERALD)
            .name("&a&lEconomy Health Score")
            .lore(
                "&7Score: " + healthColor + String.format("%.1f", health) + "/100",
                barChart(health, 100),
                "",
                "&7Transactions (24h): &f" + txCount,
                "",
                "&8A score of 100 means balanced buy/sell flow."
            )
            .glow()
            .build());

        // ── Top Bought (24h) ──────────────────────────────────────────────────
        inventory.setItem(9, new ItemBuilder(Material.GOLDEN_PICKAXE)
            .name("&6&lTop Bought Items &8(24h)")
            .lore("&7Items players bought most today")
            .build());

        long maxBoughtQty = bought.isEmpty() ? 1 : bought.get(0).quantity();
        for (int i = 0; i < 5; i++) {
            int slot = 10 + i;
            if (i < bought.size()) {
                AnalyticsRepository.ItemStat stat = bought.get(i);
                Material mat = safeMaterial(stat.itemId());
                inventory.setItem(slot, new ItemBuilder(mat)
                    .name("&e#" + (i + 1) + " " + prettify(stat.itemName() != null ? stat.itemName() : stat.itemId()))
                    .lore(
                        "&7Purchases: &f" + stat.quantity(),
                        "&7Revenue: &a$" + String.format("%.2f", stat.revenue()),
                        barChart(stat.quantity(), maxBoughtQty)
                    )
                    .build());
            } else {
                inventory.setItem(slot, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                    .name("&8No data").build());
            }
        }

        // ── Top Sold (24h) ────────────────────────────────────────────────────
        inventory.setItem(18, new ItemBuilder(Material.GOLDEN_SHOVEL)
            .name("&6&lTop Sold Items &8(24h)")
            .lore("&7Items players sold most today")
            .build());

        long maxSoldQty = sold.isEmpty() ? 1 : sold.get(0).quantity();
        for (int i = 0; i < 5; i++) {
            int slot = 19 + i;
            if (i < sold.size()) {
                AnalyticsRepository.ItemStat stat = sold.get(i);
                Material mat = safeMaterial(stat.itemId());
                inventory.setItem(slot, new ItemBuilder(mat)
                    .name("&e#" + (i + 1) + " " + prettify(stat.itemName() != null ? stat.itemName() : stat.itemId()))
                    .lore(
                        "&7Sales: &f" + stat.quantity(),
                        "&7Paid Out: &a$" + String.format("%.2f", stat.revenue()),
                        barChart(stat.quantity(), maxSoldQty)
                    )
                    .build());
            } else {
                inventory.setItem(slot, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                    .name("&8No data").build());
            }
        }

        // ── Revenue by currency ───────────────────────────────────────────────
        int revSlot = 27;
        for (var entry : revenue.entrySet()) {
            if (revSlot > 35) break;
            var eco = plugin.getEconomyRegistry().get(entry.getKey());
            String name = eco != null ? eco.getDisplayName() : entry.getKey();
            inventory.setItem(revSlot++, new ItemBuilder(Material.GOLD_INGOT)
                .name("&6" + name + " Revenue")
                .lore(
                    "&7All-time: &a" + (eco != null ? eco.format(entry.getValue()) : String.format("%.2f", entry.getValue()))
                )
                .build());
        }
        if (revenue.isEmpty()) {
            inventory.setItem(27, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name("&8No revenue data yet").build());
        }
    }

    /** ASCII bar chart rendered as a lore line. */
    private String barChart(double value, double max) {
        int filled = max > 0 ? (int) Math.round((value / max) * 20) : 0;
        StringBuilder bar = new StringBuilder("&8[");
        for (int i = 0; i < 20; i++) bar.append(i < filled ? "&a█" : "&8░");
        bar.append("&8] &7").append(String.format("%.0f", value));
        return bar.toString();
    }

    private Material safeMaterial(String itemId) {
        if (itemId == null) return Material.PAPER;
        // itemId may be "shopId:categoryId:materialName" — try the last segment
        String[] parts = itemId.split("[:/]");
        String matName = parts[parts.length - 1].toUpperCase();
        Material mat = Material.matchMaterial(matName);
        return mat != null ? mat : Material.PAPER;
    }

    private String prettify(String raw) {
        return raw.replace('_', ' ')
            .chars()
            .collect(StringBuilder::new,
                (sb, c) -> sb.append(sb.isEmpty() || sb.charAt(sb.length() - 1) == ' '
                    ? (char) Character.toUpperCase(c) : (char) c),
                StringBuilder::append)
            .toString();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            plugin.getGuiManager().goBack(player);
        } else if (slot == EXPORT_SLOT) {
            player.closeInventory();
            exportCsv(player);
        }
    }

    private void exportCsv(Player player) {
        AnalyticsRepository repo = plugin.getAnalyticsRepository();
        plugin.getMessageManager().sendRaw(player, "&e[Analytics] &7Exporting data, please wait...");

        repo.getTopBought(0, 100).thenAccept(bought ->
        repo.getTopSold(0, 100).thenAccept(sold ->
        repo.getTotalRevenue(0).thenAccept(revenue ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    // Futures return null on DB error — use safe defaults to avoid NPE
                    java.util.List<AnalyticsRepository.ItemStat> safeBought =
                        bought  != null ? bought  : java.util.List.of();
                    java.util.List<AnalyticsRepository.ItemStat> safeSold =
                        sold    != null ? sold    : java.util.List.of();
                    java.util.Map<String, Double> safeRevenue =
                        revenue != null ? revenue : java.util.Map.of();

                    java.io.File dir = new java.io.File(plugin.getDataFolder(), "exports");
                    dir.mkdirs();
                    String ts = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new java.util.Date());
                    java.io.File file = new java.io.File(dir, "analytics_" + ts + ".csv");

                    try (java.io.PrintWriter pw = new java.io.PrintWriter(file)) {
                        pw.println("# FluxShop Analytics Export — " + ts);
                        pw.println();
                        pw.println("# TOP BOUGHT ITEMS (ALL TIME)");
                        pw.println("item_id,item_name,quantity,revenue");
                        for (var s : safeBought) pw.printf("%s,%s,%d,%.2f%n",
                            s.itemId(), s.itemName() != null ? s.itemName() : "", s.quantity(), s.revenue());

                        pw.println();
                        pw.println("# TOP SOLD ITEMS (ALL TIME)");
                        pw.println("item_id,item_name,quantity,payout");
                        for (var s : safeSold) pw.printf("%s,%s,%d,%.2f%n",
                            s.itemId(), s.itemName() != null ? s.itemName() : "", s.quantity(), s.revenue());

                        pw.println();
                        pw.println("# REVENUE BY CURRENCY (ALL TIME)");
                        pw.println("currency,total");
                        for (var e : safeRevenue.entrySet()) pw.printf("%s,%.2f%n", e.getKey(), e.getValue());
                    }
                    plugin.getMessageManager().sendRaw(player, "&e[Analytics] &aExported to: plugins/FluxShop/exports/" + file.getName());
                } catch (Exception e) {
                    plugin.getMessageManager().sendRaw(player, "&e[Analytics] &cExport failed: " + e.getMessage());
                }
            })
        )));
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
