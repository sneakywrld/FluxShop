package dev.fluxshop.shop;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.economy.EconomyProvider;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.model.Transaction;
import dev.fluxshop.storage.TransactionRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;

import java.time.Instant;
import java.util.*;

/**
 * Processes /sellall — sells every eligible item in a player's inventory.
 */
public class SellAllProcessor {

    public record SellResult(int itemsSold, double totalEarned, String currency, Map<String, Double> breakdown) {}

    private final FluxShopPlugin        plugin;
    private final PriceCalculator       priceCalc;
    private final TransactionRepository txRepo;

    public SellAllProcessor(FluxShopPlugin plugin) {
        this.plugin    = plugin;
        this.priceCalc = new PriceCalculator(plugin);
        this.txRepo    = new TransactionRepository(plugin.getDatabase());
    }

    /**
     * Sells all sellable items in the player's inventory.
     *
     * @param filter Optional material filter (null = sell everything sellable)
     */
    public SellResult sellAll(Player player, Material filter) {
        // Build map: canonical item key → best sell price.
        // The key encodes material + type-specific metadata (potion type, enchant set) so that
        // e.g. POTION:SWIFTNESS and POTION:STRENGTH are tracked as separate entries.
        Map<String, ShopItemEntry> bestPrices = new LinkedHashMap<>();
        List<ShopItem> candidates = filter != null
            ? plugin.getShopManager().findSellableItems(new ItemStack(filter))
            : plugin.getShopManager().findAllSellableItems();
        for (ShopItem shopItem : candidates) {
            if (shopItem.getDisplayItem() == null) continue;
            if (shopItem.getSellPermission() != null && !player.hasPermission(shopItem.getSellPermission())) continue;
            double unitPrice = shopItem.getSellPrice();
            String key = itemKey(shopItem.getDisplayItem());
            bestPrices.merge(key, new ShopItemEntry(shopItem, unitPrice), (a, b) -> a.price() >= b.price() ? a : b);
        }

        if (bestPrices.isEmpty()) {
            plugin.getMessageManager().send(player, "sellall.nothing-to-sell");
            return new SellResult(0, 0, "", Map.of());
        }

        // Collect items to sell from inventory
        Map<String, Double> breakdown = new LinkedHashMap<>();
        double totalEarned = 0;
        int    itemsSold   = 0;
        String currency    = "";

        // Sort by sort config
        String sort = plugin.getConfigManager().getString("shop.sellall-sort", "PRICE_HIGH");
        List<Map.Entry<String, ShopItemEntry>> sorted = new ArrayList<>(bestPrices.entrySet());
        sorted.sort((a, b) -> switch (sort) {
            case "PRICE_HIGH" -> Double.compare(b.getValue().price(), a.getValue().price());
            case "PRICE_LOW"  -> Double.compare(a.getValue().price(), b.getValue().price());
            default           -> 0;
        });

        for (Map.Entry<String, ShopItemEntry> entry : sorted) {
            ShopItem shopItem = entry.getValue().item();
            int count = countAndRemove(player, shopItem.getDisplayItem());
            if (count <= 0) continue;

            EconomyProvider eco = plugin.getEconomyRegistry().get(shopItem.getEconomyProvider());
            if (eco == null) eco = plugin.getEconomyRegistry().getDefault();
            if (eco == null) {
                // Return items — no economy provider available
                returnItems(player, shopItem.getDisplayItem(), count);
                continue;
            }
            double pricePerUnit = priceCalc.getSellPrice(shopItem, player, 1);
            double earned       = Math.round(pricePerUnit * count * 100.0) / 100.0;

            if (!eco.deposit(player.getUniqueId(), earned)) {
                // Deposit failed — return items so player doesn't lose them silently
                returnItems(player, shopItem.getDisplayItem(), count);
                plugin.getLogger().warning("[SellAll] Deposit of " + earned + " failed for " + player.getName() + " — items returned.");
                continue;
            }
            totalEarned  += earned;
            itemsSold    += count;
            currency      = eco.getCurrencyName();
            String label  = shopItem.getCustomName() != null ? shopItem.getCustomName()
                          : shopItem.getDisplayItem().getType().name();
            breakdown.merge(label, earned, Double::sum);

            // Log
            txRepo.log(new Transaction(0, player.getUniqueId(), player.getName(),
                Transaction.Type.SELL, shopItem.getShopId(), shopItem.getCategoryId(),
                shopItem.getId(), label, count, pricePerUnit, eco.getId(), Instant.now()));
        }

        if (itemsSold == 0) {
            plugin.getMessageManager().send(player, "sellall.nothing-to-sell");
            return new SellResult(0, 0, "", Map.of());
        }

        EconomyProvider defaultEco = plugin.getEconomyRegistry().getDefault();
        String totalStr = defaultEco != null ? defaultEco.format(totalEarned) : String.format("%.2f", totalEarned);
        plugin.getMessageManager().send(player, "sellall.sold",
            "items", itemsSold,
            "total", totalStr,
            "currency", currency);
        plugin.getSoundManager().play(player, "sell-success");
        plugin.getParticleManager().spawn(player, "sell-success");

        return new SellResult(itemsSold, totalEarned, currency, breakdown);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns {@code count} copies of {@code template} to the player's inventory,
     * dropping any overflow at their feet.
     */
    private void returnItems(Player player, ItemStack template, int count) {
        ItemStack returning = template.clone();
        returning.setAmount(count);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(returning);
        leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
    }

    /**
     * Removes all inventory items that match {@code template} (metadata-aware) and returns the total count.
     */
    private int countAndRemove(Player player, ItemStack template) {
        int count = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack slot = contents[i];
            if (itemMatches(slot, template)) {
                count += slot.getAmount();
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
        return count;
    }

    /**
     * Returns a canonical string key for an ItemStack that distinguishes items whose
     * type alone is insufficient (potions, enchanted books).
     */
    @SuppressWarnings("deprecation")
    private String itemKey(ItemStack item) {
        // ItemsAdder items: key by namespace:id — two IA items of the same base material
        // are distinct items and must not share a price-map slot.
        if (plugin.getCompatManager().hasItemsAdder()) {
            String iaId = plugin.getCompatManager().getItemsAdderItemId(item);
            if (iaId != null) return "IA:" + iaId;
        }

        String base = item.getType().name();
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof PotionMeta pm) {
            PotionData pd = pm.getBasePotionData();
            return base + ":" + (pd != null ? pd.getType().name() : "WATER");
        }
        if (meta instanceof EnchantmentStorageMeta esm) {
            // Sort entries for a stable key regardless of insertion order
            return base + ":" + new TreeMap<>(esm.getStoredEnchants());
        }
        return base;
    }

    /**
     * Returns true if {@code candidate} is the same sellable item as {@code template}.
     * Mirrors the logic in {@link TransactionProcessor#itemMatches}.
     */
    @SuppressWarnings("deprecation")
    private boolean itemMatches(ItemStack candidate, ItemStack template) {
        if (candidate == null || candidate.getType() != template.getType()) return false;

        // ItemsAdder: match by namespace:id, not just material
        if (plugin.getCompatManager().hasItemsAdder()) {
            String templateIAId  = plugin.getCompatManager().getItemsAdderItemId(template);
            String candidateIAId = plugin.getCompatManager().getItemsAdderItemId(candidate);
            if (templateIAId != null) return templateIAId.equals(candidateIAId);
            if (candidateIAId != null) return false; // template is vanilla, candidate is IA
        }

        ItemMeta tMeta = template.getItemMeta();
        ItemMeta cMeta = candidate.getItemMeta();
        if (tMeta instanceof PotionMeta tpm && cMeta instanceof PotionMeta cpm) {
            PotionData td = tpm.getBasePotionData();
            PotionData cd = cpm.getBasePotionData();
            if (td == null) return true;
            return cd != null && td.getType() == cd.getType();
        }
        if (tMeta instanceof EnchantmentStorageMeta tesm && cMeta instanceof EnchantmentStorageMeta cesm) {
            if (tesm.getStoredEnchants().isEmpty()) return true;
            return tesm.getStoredEnchants().equals(cesm.getStoredEnchants());
        }
        return true;
    }

    private record ShopItemEntry(ShopItem item, double price) {}
}
