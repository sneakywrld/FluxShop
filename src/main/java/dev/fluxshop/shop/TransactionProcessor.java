package dev.fluxshop.shop;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.api.event.ShopPurchaseEvent;
import dev.fluxshop.api.event.ShopSellEvent;
import dev.fluxshop.economy.EconomyProvider;
import dev.fluxshop.model.ShopItem;
import dev.fluxshop.model.Transaction;
import dev.fluxshop.storage.TransactionRepository;
import dev.fluxshop.storage.StockRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes atomic buy and sell transactions.
 *
 * <p>Transaction flow for a <b>buy</b>:
 * <ol>
 *   <li>Fire {@link ShopPurchaseEvent} — allow cancellation</li>
 *   <li>Check player has sufficient funds</li>
 *   <li>Check global stock</li>
 *   <li>Check per-player limit</li>
 *   <li>Check player has inventory space</li>
 *   <li>Withdraw funds from economy</li>
 *   <li>Give items to player</li>
 *   <li>Execute on-buy commands</li>
 *   <li>Update stock counters</li>
 *   <li>Log transaction</li>
 *   <li>Send success message + particles + sound</li>
 * </ol>
 */
public class TransactionProcessor {

    /** Last purchase timestamp per player (ms). Shared across all instances. */
    private static final Map<UUID, Long> lastPurchase = new ConcurrentHashMap<>();

    public enum Result {
        SUCCESS,
        CANCELLED,       // ShopPurchaseEvent cancelled
        NO_FUNDS,
        NO_STOCK,
        PLAYER_LIMIT,
        INVENTORY_FULL,
        NO_ITEMS,        // Player has none of this item (sell)
        ECONOMY_ERROR,
        NO_PERMISSION
    }

    private final FluxShopPlugin       plugin;
    private final PriceCalculator      priceCalc;
    private final StockManager         stockManager;
    private final TransactionRepository txRepo;
    private final StockRepository       stockRepo;

    public TransactionProcessor(FluxShopPlugin plugin) {
        this.plugin      = plugin;
        this.priceCalc   = new PriceCalculator(plugin);
        this.stockManager = new StockManager(plugin);
        this.txRepo      = new TransactionRepository(plugin.getDatabase());
        this.stockRepo   = new StockRepository(plugin.getDatabase());
    }

    // ── Buy ───────────────────────────────────────────────────────────────────

    public Result buy(Player player, ShopItem item, int quantity) {
        // 0. Purchase cooldown (ticks → ms)
        int cooldownTicks = plugin.getConfigManager().getInt("shop.purchase-cooldown-ticks", 4);
        if (cooldownTicks > 0 && !player.hasPermission("fluxshop.bypass.cooldown")) {
            long cooldownMs = cooldownTicks * 50L; // 1 tick = 50 ms
            long last       = lastPurchase.getOrDefault(player.getUniqueId(), 0L);
            long remaining  = cooldownMs - (System.currentTimeMillis() - last);
            if (remaining > 0) {
                plugin.getMessageManager().send(player, "transaction.cooldown",
                    "seconds", String.format("%.1f", remaining / 1000.0));
                return Result.CANCELLED;
            }
        }

        // 1. Permission check
        if (item.getBuyPermission() != null && !player.hasPermission(item.getBuyPermission())) {
            return Result.NO_PERMISSION;
        }

        // 2. Price calculation
        EconomyProvider economy = getEconomy(item);
        if (economy == null) return Result.ECONOMY_ERROR;
        double totalPrice = priceCalc.getBuyPrice(item, player, quantity);
        if (totalPrice < 0) return Result.NO_STOCK;

        // 3. Fire cancellable event
        ShopPurchaseEvent event = new ShopPurchaseEvent(player, item, quantity, totalPrice, economy.getId());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return Result.CANCELLED;

        // Use potentially-modified price from event
        double finalPrice = event.getFinalPrice();

        // 4. Stock checks
        Result stockCheck = stockManager.canBuy(player, item, quantity);
        if (stockCheck != Result.SUCCESS) return stockCheck;

        // 5. Funds check
        if (!economy.has(player.getUniqueId(), finalPrice)) return Result.NO_FUNDS;

        // 6. Inventory space check
        ItemStack giveItem = resolveGiveItem(item);
        giveItem.setAmount(quantity);
        if (!hasInventorySpace(player, giveItem)) {
            if (!plugin.getConfigManager().getBoolean("general.drop-items-on-full-inventory", true)) {
                return Result.INVENTORY_FULL;
            }
        }

        // 7. Withdraw funds
        if (!economy.withdraw(player.getUniqueId(), finalPrice)) return Result.ECONOMY_ERROR;

        // 8. Give items (drop if inventory full)
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(giveItem);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        // 9. Execute on-buy commands
        for (String cmd : item.getOnBuyCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                cmd.replace("{player}", player.getName())
                   .replace("{amount}", String.valueOf(quantity)));
        }

        // 10. Update stock
        stockRepo.recordPurchase(item.getShopId(), item.getId(), player.getUniqueId(), quantity);

        // 11. Log transaction
        Transaction tx = new Transaction(0, player.getUniqueId(), player.getName(),
            Transaction.Type.BUY,
            item.getShopId(), item.getCategoryId(), item.getId(),
            item.getCustomName() != null ? item.getCustomName() : item.getDisplayItem().getType().name(),
            quantity, finalPrice / quantity, economy.getId(), Instant.now());
        txRepo.log(tx);
        plugin.getDiscordLogger().logTransaction(player, tx);

        // 12. Record cooldown timestamp
        lastPurchase.put(player.getUniqueId(), System.currentTimeMillis());

        // 13. Feedback
        plugin.getMessageManager().send(player, "transaction.buy-success",
            "amount", quantity,
            "item", displayName(item),
            "price", economy.format(finalPrice),
            "currency", economy.getCurrencyName());
        plugin.getSoundManager().play(player, "purchase-success");
        plugin.getParticleManager().spawn(player, "purchase-success");

        return Result.SUCCESS;
    }

    // ── Sell ─────────────────────────────────────────────────────────────────

    public Result sell(Player player, ShopItem item, int quantity) {
        if (item.getSellPermission() != null && !player.hasPermission(item.getSellPermission())) {
            return Result.NO_PERMISSION;
        }

        EconomyProvider economy = getEconomy(item);
        if (economy == null) return Result.ECONOMY_ERROR;
        double totalPrice = priceCalc.getSellPrice(item, player, quantity);
        if (totalPrice < 0) return Result.NO_STOCK; // item.isSellable() was false

        // Check player has the items
        int playerHas = countItems(player, item.getDisplayItem());
        if (playerHas < quantity) return Result.NO_ITEMS;

        // Fire cancellable event
        ShopSellEvent event = new ShopSellEvent(player, item, quantity, totalPrice, economy.getId());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return Result.CANCELLED;

        double finalPrice = event.getFinalPrice();

        // Pay player first — so we never remove items without compensation
        if (!economy.deposit(player.getUniqueId(), finalPrice)) return Result.ECONOMY_ERROR;

        // Remove items after successful payment
        removeItems(player, item.getDisplayItem(), quantity);

        // Execute on-sell commands
        for (String cmd : item.getOnSellCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                cmd.replace("{player}", player.getName())
                   .replace("{amount}", String.valueOf(quantity)));
        }

        // Log
        Transaction sellTx = new Transaction(0, player.getUniqueId(), player.getName(),
            Transaction.Type.SELL,
            item.getShopId(), item.getCategoryId(), item.getId(),
            displayName(item), quantity, finalPrice / quantity, economy.getId(), Instant.now());
        txRepo.log(sellTx);
        plugin.getDiscordLogger().logTransaction(player, sellTx);

        // Feedback
        plugin.getMessageManager().send(player, "transaction.sell-success",
            "amount", quantity,
            "item", displayName(item),
            "price", economy.format(finalPrice),
            "currency", economy.getCurrencyName());
        plugin.getSoundManager().play(player, "sell-success");
        plugin.getParticleManager().spawn(player, "sell-success");

        return Result.SUCCESS;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the ItemStack to give the player on purchase.
     *
     * For MythicMobs items a fresh copy is generated on each call so that
     * randomised stats, abilities, and lore are applied correctly rather than
     * reusing the snapshot created at plugin load time.
     */
    private ItemStack resolveGiveItem(ShopItem item) {
        // MythicMobs: always generate a fresh copy so randomised stats/lore are applied
        String mythicId = item.getMythicItemId();
        if (mythicId != null) {
            ItemStack fresh = plugin.getCompatManager().getMythicItem(mythicId);
            if (fresh != null) return fresh;
        }
        // ItemsAdder: generate a fresh copy so all IA NBT tags are guaranteed present
        String iaId = item.getItemsAdderId();
        if (iaId != null) {
            ItemStack fresh = plugin.getCompatManager().getItemsAdderItem(iaId);
            if (fresh != null) return fresh;
        }
        return item.getDisplayItem().clone();
    }

    private EconomyProvider getEconomy(ShopItem item) {
        EconomyProvider eco = plugin.getEconomyRegistry().get(item.getEconomyProvider());
        if (eco == null) eco = plugin.getEconomyRegistry().getDefault();
        return eco;
    }

    private boolean hasInventorySpace(Player player, ItemStack item) {
        // Fast path: empty slot available
        if (player.getInventory().firstEmpty() != -1) return true;
        // Slow path: item may stack into a partially-filled slot
        int maxStack = item.getMaxStackSize();
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot != null && slot.isSimilar(item) && slot.getAmount() < maxStack) {
                return true;
            }
        }
        return false;
    }

    private int countItems(Player player, ItemStack template) {
        int count = 0;
        for (ItemStack i : player.getInventory().getContents()) {
            if (itemMatches(i, template)) count += i.getAmount();
        }
        return count;
    }

    private void removeItems(Player player, ItemStack template, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack slot = contents[i];
            if (itemMatches(slot, template)) {
                int take = Math.min(slot.getAmount(), remaining);
                slot.setAmount(slot.getAmount() - take);
                remaining -= take;
                if (slot.getAmount() == 0) contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
    }

    /**
     * Returns true if {@code candidate} is the same sellable item as {@code template}.
     *
     * <p>Matching rules, applied in priority order:
     * <ol>
     *   <li>Material must match (fast pre-check).</li>
     *   <li>MythicMobs items — compared by internal MYTHIC_TYPE NBT tag so that
     *       e.g. a mythic "FlameSword" does not match a vanilla diamond sword of the
     *       same material.</li>
     *   <li>Potions — compared by potion type (Speed ≠ Strength).</li>
     *   <li>Enchanted books — compared by stored enchantments.</li>
     *   <li>All other items — material match is sufficient.</li>
     * </ol>
     */
    @SuppressWarnings("deprecation")
    private boolean itemMatches(ItemStack candidate, ItemStack template) {
        if (candidate == null || candidate.getType() != template.getType()) return false;

        // MythicMobs: match strictly by internal mythic item ID stored in NBT.
        // This prevents a vanilla sword from matching a mythic sword of the same material.
        if (plugin.getCompatManager().isMythicItem(template)) {
            String templateMythicId  = plugin.getCompatManager().getMythicItemId(template);
            String candidateMythicId = plugin.getCompatManager().getMythicItemId(candidate);
            // Template has a known mythic ID — candidate must have the same ID.
            if (templateMythicId != null) {
                return templateMythicId.equals(candidateMythicId);
            }
        }

        // ItemsAdder: match strictly by namespace:id so that e.g. an IA "ruby sword"
        // (DIAMOND_SWORD material) does not match a vanilla DIAMOND_SWORD, and two
        // different IA items sharing the same base material don't cross-match.
        if (plugin.getCompatManager().hasItemsAdder()) {
            String templateIAId  = plugin.getCompatManager().getItemsAdderItemId(template);
            String candidateIAId = plugin.getCompatManager().getItemsAdderItemId(candidate);
            if (templateIAId != null) {
                // Template is an IA item — candidate must carry the same IA id
                return templateIAId.equals(candidateIAId);
            }
            if (candidateIAId != null) {
                // Template is vanilla but candidate is an IA item — not the same item
                return false;
            }
        }

        ItemMeta tMeta = template.getItemMeta();
        ItemMeta cMeta = candidate.getItemMeta();

        // Potion type matching (uses deprecated PotionData API, stable 1.9–1.21)
        if (tMeta instanceof PotionMeta tpm && cMeta instanceof PotionMeta cpm) {
            PotionData td = tpm.getBasePotionData();
            PotionData cd = cpm.getBasePotionData();
            // A template with no explicit type (WATER/null) matches any potion of that material
            if (td == null) return true;
            return cd != null && Objects.equals(td.getType(), cd.getType());
        }

        // Enchanted book matching: stored enchantments must match exactly
        if (tMeta instanceof EnchantmentStorageMeta tesm && cMeta instanceof EnchantmentStorageMeta cesm) {
            // A template book with no stored enchants matches any enchanted book (fallback)
            if (tesm.getStoredEnchants().isEmpty()) return true;
            return tesm.getStoredEnchants().equals(cesm.getStoredEnchants());
        }

        return true;
    }

    private String displayName(ShopItem item) {
        if (item.getCustomName() != null) return item.getCustomName();
        String mat = item.getDisplayItem().getType().name();
        return mat.charAt(0) + mat.substring(1).toLowerCase().replace('_', ' ');
    }
}
