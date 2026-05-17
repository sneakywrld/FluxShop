package dev.fluxshop.blackmarket;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.api.event.BlackMarketPurchaseEvent;
import dev.fluxshop.economy.EconomyProvider;
import dev.fluxshop.model.BlackMarketListing;
import dev.fluxshop.storage.BlackMarketRepository;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Manages the rotating Black Market: scheduling rotations, item pool selection,
 * per-player purchase limits, and the atomic purchase flow.
 */
public class BlackMarketService {

    private final FluxShopPlugin       plugin;
    private final BlackMarketRepository repo;

    /** Current rotation listings, cached in memory. */
    private final List<BlackMarketListing> currentListings = new ArrayList<>();

    /** Per-player purchase counts for the current rotation: listingId → uuid → count */
    private final Map<Long, Map<UUID, Integer>> playerPurchases = new ConcurrentHashMap<>();

    private BukkitTask rotationTask;
    private Instant    nextRotation;

    // ── Config helpers (read at call-time so /fluxshop reload takes effect) ──────
    private static final int    ROTATION_HOURS      = 6;
    private static final int    DEFAULT_STOCK       = 10;

    private int getItemsPerRotation() {
        return plugin.getConfigManager().getInt("black-market.items-per-rotation", 6);
    }
    private int getDefaultMaxPerPlayer() {
        return plugin.getConfigManager().getInt("black-market.max-purchases-per-player", 3);
    }

    public BlackMarketService(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.repo   = new BlackMarketRepository(plugin);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        // Load current rotation from DB
        repo.getAll().thenAccept(listings -> {
            if (listings == null || listings.isEmpty()) {
                // First run — generate initial rotation
                rotate();
            } else {
                currentListings.addAll(listings);
                nextRotation = listings.isEmpty() ? Instant.now()
                    : listings.get(0).getNextRefresh();
                plugin.getLogger().info("BlackMarket loaded " + listings.size() + " listings.");
            }
        });

        // Check for rotation every minute
        rotationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (nextRotation != null && Instant.now().isAfter(nextRotation)) {
                rotate();
            }
        }, 20L * 60, 20L * 60);
    }

    public void stop() {
        if (rotationTask != null) rotationTask.cancel();
    }

    // ── Purchase ──────────────────────────────────────────────────────────────

    /**
     * Attempts to purchase a black market listing.
     *
     * @return null on success, or an error message
     */
    public String purchase(Player buyer, BlackMarketListing listing, int quantity) {
        if (!listing.isInStock()) return "This item is out of stock.";

        // Per-player limit check
        int alreadyBought = playerPurchases
            .getOrDefault(listing.getId(), Collections.emptyMap())
            .getOrDefault(buyer.getUniqueId(), 0);
        int limit = listing.getMaxPerPlayer();
        if (limit > 0 && alreadyBought + quantity > limit) {
            return "You can only buy " + (limit - alreadyBought) + " more of this item.";
        }

        if (quantity > listing.getRemainingStock()) {
            return "Only " + listing.getRemainingStock() + " remaining in stock.";
        }

        // Guard item data before taking payment
        if (listing.getItem() == null) return "Item data is missing for this listing.";

        double totalCost = listing.getPrice() * quantity;
        EconomyProvider eco = plugin.getEconomyRegistry().get(listing.getCurrency());
        if (eco == null) return "Economy provider not available.";
        if (!eco.has(buyer.getUniqueId(), totalCost)) return "You cannot afford this item.";

        // Fire cancellable event
        BlackMarketPurchaseEvent event = new BlackMarketPurchaseEvent(buyer, listing, totalCost);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return "Purchase was cancelled.";
        totalCost = event.getPrice();

        eco.withdraw(buyer.getUniqueId(), totalCost);
        listing.setPurchased(listing.getPurchased() + quantity);

        // Log to Discord
        plugin.getDiscordLogger().logBlackMarket(buyer, listing, quantity, totalCost);

        // Track per-player
        playerPurchases
            .computeIfAbsent(listing.getId(), k -> new ConcurrentHashMap<>())
            .merge(buyer.getUniqueId(), quantity, Integer::sum);

        // Persist
        repo.recordPurchase(listing.getId(), buyer.getUniqueId(), quantity);
        ItemStack toGive = listing.getItem().clone();
        toGive.setAmount(quantity);
        Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(toGive);
        leftover.values().forEach(item -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), item));

        plugin.getMessageManager().send(buyer, "black-market.purchased",
            "item", toGive.getType().name().toLowerCase().replace('_', ' '),
            "price", eco.format(totalCost),
            "currency", eco.getCurrencyName());
        return null;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<BlackMarketListing> getCurrentListings() {
        synchronized (currentListings) {
            return Collections.unmodifiableList(new ArrayList<>(currentListings));
        }
    }

    public Instant getNextRotation() {
        return nextRotation;
    }

    public int getPlayerPurchaseCount(long listingId, UUID uuid) {
        return playerPurchases
            .getOrDefault(listingId, Collections.emptyMap())
            .getOrDefault(uuid, 0);
    }

    // ── Rotation ──────────────────────────────────────────────────────────────

    private void rotate() {
        try {
            synchronized (currentListings) {
                currentListings.clear();
            }
            playerPurchases.clear();
            repo.clearAll();

            List<BlackMarketListing> pool = buildItemPool();
            if (pool.isEmpty()) {
                plugin.getLogger().warning("BlackMarket item pool is empty — check black_market_pool.yml");
                return;
            }

            Collections.shuffle(pool, ThreadLocalRandom.current());
            int count = Math.min(getItemsPerRotation(), pool.size());
            nextRotation = Instant.now().plus(ROTATION_HOURS, ChronoUnit.HOURS);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                BlackMarketListing listing = pool.get(i);
                listing.setNextRefresh(nextRotation);
                CompletableFuture<Void> f = repo.insert(listing).thenAccept(saved -> {
                    synchronized (currentListings) {
                        currentListings.add(saved);
                    }
                });
                futures.add(f);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> plugin.getLogger().info(
                    "BlackMarket rotation complete: " + currentListings.size() + " listings persisted."));

            if (plugin.getConfigManager().getBoolean("black-market.announce-rotation", true)) {
                // Message lives in messages.yml at black-market.rotation-announce, not config.yml
                String announcement = plugin.getMessageManager().format(
                    plugin.getMessageManager().getRaw("black-market.rotation-announce"));
                Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.broadcastMessage(announcement));
            }

            plugin.getLogger().info("BlackMarket rotated: " + count + " new items. Next rotation: " + nextRotation);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "BlackMarket rotation error", e);
        }
    }

    /**
     * Loads the pool of possible items from black_market_pool.yml in the plugin's data folder.
     * Falls back to a set of built-in items if the file doesn't exist.
     */
    private List<BlackMarketListing> buildItemPool() {
        File poolFile = new File(plugin.getDataFolder(), "black_market_pool.yml");
        if (poolFile.exists()) {
            return loadPoolFromFile(poolFile);
        }
        return buildDefaultPool();
    }

    private List<BlackMarketListing> loadPoolFromFile(File file) {
        List<BlackMarketListing> pool = new ArrayList<>();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            try {
                ItemStack listingItem = resolvePoolItem(yml, key);
                if (listingItem == null) {
                    plugin.getLogger().warning("BlackMarket pool entry '" + key
                        + "' has no valid material or items-adder/oraxen id — skipping.");
                    continue;
                }

                String displayName = yml.getString(key + ".display-name");
                if (displayName != null && !displayName.isEmpty()) {
                    org.bukkit.inventory.meta.ItemMeta im = listingItem.getItemMeta();
                    if (im != null) {
                        im.setDisplayName(net.md_5.bungee.api.ChatColor
                            .translateAlternateColorCodes('&', displayName));
                        listingItem.setItemMeta(im);
                    }
                }

                BlackMarketListing l = new BlackMarketListing();
                l.setItem(listingItem);
                l.setPrice(yml.getDouble(key + ".price", 100));
                l.setCurrency(yml.getString(key + ".currency",
                    plugin.getEconomyRegistry().getDefault() != null
                        ? plugin.getEconomyRegistry().getDefault().getId() : "vault"));
                l.setStock(yml.getInt(key + ".stock", DEFAULT_STOCK));
                l.setMaxPerPlayer(yml.getInt(key + ".max-per-player", getDefaultMaxPerPlayer()));
                pool.add(l);
            } catch (Exception e) {
                plugin.getLogger().warning("BlackMarket pool entry '" + key + "' is invalid: " + e.getMessage());
            }
        }
        return pool;
    }

    /**
     * Resolves a pool entry's ItemStack from its YAML section.
     * Priority: items-adder > oraxen > material.
     * Returns null if no resolvable item type is found.
     */
    private ItemStack resolvePoolItem(YamlConfiguration yml, String key) {
        // ItemsAdder custom item
        String iaId = yml.getString(key + ".items-adder");
        if (iaId != null) {
            ItemStack ia = plugin.getCompatManager().getItemsAdderItem(iaId);
            if (ia != null) return ia;
            plugin.getLogger().warning("BlackMarket pool entry '" + key
                + "': ItemsAdder item '" + iaId + "' not found (ItemsAdder loaded="
                + plugin.getCompatManager().hasItemsAdder() + ").");
            return null;
        }

        // Oraxen custom item
        String oraxenId = yml.getString(key + ".oraxen");
        if (oraxenId != null) {
            ItemStack oraxen = plugin.getCompatManager().getOraxenItem(oraxenId);
            if (oraxen != null) return oraxen;
            plugin.getLogger().warning("BlackMarket pool entry '" + key
                + "': Oraxen item '" + oraxenId + "' not found.");
            return null;
        }

        // Vanilla material
        String matName = yml.getString(key + ".material");
        if (matName == null) return null;
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(matName);
        if (mat == null) {
            plugin.getLogger().warning("BlackMarket pool entry '" + key
                + "': unknown material '" + matName + "'.");
            return null;
        }
        return new ItemStack(mat);
    }

    private List<BlackMarketListing> buildDefaultPool() {
        String eco = plugin.getEconomyRegistry().getDefault() != null
            ? plugin.getEconomyRegistry().getDefault().getId() : "vault";

        Object[][] items = {
            { "ENCHANTED_GOLDEN_APPLE", 8000.0 },
            { "NETHERITE_INGOT",        3000.0 },
            { "ELYTRA",                 5000.0 },
            { "TOTEM_OF_UNDYING",       2000.0 },
            { "NETHER_STAR",            4000.0 },
            { "HEART_OF_THE_SEA",        800.0 },
            { "TRIDENT",                 400.0 },
            { "SHULKER_SHELL",           150.0 },
            { "DRAGON_HEAD",            3000.0 },
            { "WITHER_SKELETON_SKULL",  1200.0 },
            { "END_CRYSTAL",             500.0 },
            { "ANCIENT_DEBRIS",         2000.0 },
            { "SNIFFER_EGG",            1500.0 },
            { "MUSIC_DISC_PIGSTEP",      800.0 },
        };

        List<BlackMarketListing> pool = new ArrayList<>();
        for (Object[] entry : items) {
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial((String) entry[0]);
            if (mat == null) continue;
            BlackMarketListing l = new BlackMarketListing();
            l.setItem(new ItemStack(mat));
            l.setPrice((Double) entry[1]);
            l.setCurrency(eco);
            l.setStock(DEFAULT_STOCK);
            l.setMaxPerPlayer(getDefaultMaxPerPlayer());
            pool.add(l);
        }
        return pool;
    }

}
