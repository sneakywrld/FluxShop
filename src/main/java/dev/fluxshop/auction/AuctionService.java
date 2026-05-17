package dev.fluxshop.auction;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.api.event.AuctionBidEvent;
import dev.fluxshop.api.event.AuctionListEvent;
import dev.fluxshop.api.event.AuctionSoldEvent;
import dev.fluxshop.economy.EconomyProvider;
import dev.fluxshop.model.AuctionListing;
import dev.fluxshop.storage.AuctionRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Full auction-house lifecycle: list, bid, buy-now, expiry, collect.
 *
 * <p>Active listings are cached in memory; the DB is the source of truth.
 * An async scheduler runs every minute to expire overdue listings.
 */
public class AuctionService {

    private final FluxShopPlugin    plugin;
    private final AuctionRepository repo;

    /** In-memory cache keyed by listing id. */
    private final Map<Long, AuctionListing> cache = new ConcurrentHashMap<>();

    /** Items waiting to be collected: uuid → list of ItemStack. */
    private final Map<UUID, List<ItemStack>> collectItems = new ConcurrentHashMap<>();

    /** Currency balances to be returned (outbid refunds, expiry refunds).
     *  Outer key = player UUID; inner key = currency ID; value = amount owed. */
    private final Map<UUID, Map<String, Double>> collectFunds = new ConcurrentHashMap<>();

    private BukkitTask expiryTask;

    // ── Config helpers (read at call-time so /fluxshop reload takes effect) ──────
    private double getBidIncrement() {
        return plugin.getConfigManager().getDouble("auction.bid-increment-percent", 5.0);
    }
    private int getAntiSnipeWindowSeconds() {
        return plugin.getConfigManager().getInt("auction.anti-snipe-window-seconds", 30);
    }
    private int getAntiSnipeExtendSeconds() {
        return plugin.getConfigManager().getInt("auction.anti-snipe-extend-seconds", 30);
    }
    private double getAuctionFeePercent() {
        return plugin.getConfigManager().getDouble("auction.listing-fee-percent", 5.0);
    }
    private int getMaxListingsPerPlayer() {
        return plugin.getConfigManager().getInt("auction.max-listings-per-player", 5);
    }

    public AuctionService(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.repo   = new AuctionRepository(plugin);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        // Load active listings into cache
        repo.getActive().thenAccept(list -> {
            if (list == null) { plugin.getLogger().warning("AuctionService: failed to load active listings from DB."); return; }
            list.forEach(l -> cache.put(l.getId(), l));
            plugin.getLogger().info("AuctionService loaded " + list.size() + " active listings.");
        });

        // Run expiry check every 60 seconds
        expiryTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
            this::processExpired, 20L * 60, 20L * 60);
    }

    public void stop() {
        if (expiryTask != null) expiryTask.cancel();
    }

    // ── List item ─────────────────────────────────────────────────────────────

    /**
     * Creates a new auction listing.
     *
     * @return a future with an error message string, or null on success
     */
    public CompletableFuture<String> listItem(Player seller, ItemStack item,
                                               double startPrice, double buyNow,
                                               int durationHours, String currency) {
        // Check per-player listing cap
        long owned = cache.values().stream()
            .filter(l -> l.isActive() && l.getSellerUuid().equals(seller.getUniqueId()))
            .count();
        int maxListings = getMaxListingsPerPlayer();
        if (owned >= maxListings) {
            return CompletableFuture.completedFuture(
                "You already have " + maxListings + " active listings.");
        }

        AuctionListing listing = new AuctionListing();
        listing.setSellerUuid(seller.getUniqueId());
        listing.setSellerName(seller.getName());
        listing.setItem(item.clone());
        listing.setStartPrice(startPrice);
        listing.setBuyNowPrice(buyNow > 0 ? buyNow : -1);
        listing.setCurrentBid(0);
        listing.setCurrency(currency);
        listing.setCreatedAt(Instant.now());
        listing.setExpiresAt(Instant.now().plus(durationHours, ChronoUnit.HOURS));
        listing.setStatus(AuctionListing.Status.ACTIVE);

        // Fire cancellable event on main thread
        AuctionListEvent event = new AuctionListEvent(seller, listing);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return CompletableFuture.completedFuture("Listing was cancelled by a plugin.");

        plugin.getDiscordLogger().logAuctionList(seller, listing);

        // Remove item from player inventory
        seller.getInventory().removeItem(item);

        return repo.insert(listing).thenApply(saved -> {
            // saved is null (Database.async caught an exception) or id==0 (repository
            // caught the SQLException internally and returned the unmodified listing)
            if (saved == null || saved.getId() == 0) {
                plugin.getLogger().warning("AuctionService: insert returned no id for "
                    + seller.getName() + " — returning item.");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (seller.isOnline()) {
                        seller.getInventory().addItem(item.clone());
                        plugin.getMessageManager().sendRaw(seller,
                            "&c[Auction] Listing failed — your item has been returned.");
                    }
                });
                return "Database error. Please try again.";
            }
            cache.put(saved.getId(), saved);
            return (String) null; // null = success
        }).exceptionally(ex -> {
            // Fallback for unexpected RuntimeExceptions escaping the repository
            plugin.getLogger().log(Level.WARNING, "Failed to insert auction listing for "
                + seller.getName(), ex);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (seller.isOnline()) {
                    seller.getInventory().addItem(item.clone());
                    plugin.getMessageManager().sendRaw(seller,
                        "&c[Auction] Listing failed — your item has been returned.");
                }
            });
            return "Database error. Please try again.";
        });
    }

    // ── Bid ───────────────────────────────────────────────────────────────────

    /**
     * Places a bid on a listing.
     *
     * @return null on success, or an error message
     */
    public String placeBid(Player bidder, AuctionListing listing, double amount) {
        if (!listing.isActive()) return "This listing is no longer active.";
        if (listing.getSellerUuid().equals(bidder.getUniqueId())) return "You cannot bid on your own listing.";

        double minBid = listing.getMinNextBid(getBidIncrement());
        if (amount < minBid) return String.format("Minimum bid is %.2f.", minBid);

        EconomyProvider eco = plugin.getEconomyRegistry().get(listing.getCurrency());
        if (eco == null) return "Economy provider not available.";
        if (!eco.has(bidder.getUniqueId(), amount)) return "You cannot afford this bid.";

        // Fire cancellable event
        AuctionBidEvent event = new AuctionBidEvent(bidder, listing, amount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return "Bid was cancelled.";
        amount = event.getBidAmount();

        // Refund previous bidder
        if (listing.hasBid()) {
            refundBidder(listing.getCurrentBidder(), listing.getCurrentBid(), listing.getCurrency());
        }

        // Take funds from new bidder
        eco.withdraw(bidder.getUniqueId(), amount);

        // Update listing
        listing.setCurrentBid(amount);
        listing.setCurrentBidder(bidder.getUniqueId());
        listing.setCurrentBidderName(bidder.getName());

        // Anti-snipe: extend timer if bid placed in last N seconds
        Instant now = Instant.now();
        if (listing.getExpiresAt().minusSeconds(getAntiSnipeWindowSeconds()).isBefore(now)) {
            listing.setExpiresAt(now.plusSeconds(getAntiSnipeExtendSeconds()));
        }

        repo.updateBid(listing);
        cache.put(listing.getId(), listing);

        plugin.getDiscordLogger().logAuctionBid(bidder, listing, amount);

        // Notify seller if online
        Player seller = Bukkit.getPlayer(listing.getSellerUuid());
        if (seller != null) {
            plugin.getMessageManager().send(seller, "auction.seller-bid",
                "player", bidder.getName(),
                "bid", eco.format(amount),
                "currency", eco.getCurrencyName());
        }

        return null; // success
    }

    // ── Buy Now ───────────────────────────────────────────────────────────────

    /**
     * Instantly purchases a listing at the buy-now price.
     *
     * @return null on success, or an error message
     */
    public String buyNow(Player buyer, AuctionListing listing) {
        if (!listing.isActive())  return "This listing is no longer active.";
        if (!listing.hasBuyNow()) return "This listing has no buy-now option.";
        if (listing.getSellerUuid().equals(buyer.getUniqueId())) return "You cannot buy your own listing.";

        EconomyProvider eco = plugin.getEconomyRegistry().get(listing.getCurrency());
        if (eco == null) return "Economy provider not available.";

        double price = listing.getBuyNowPrice();
        if (!eco.has(buyer.getUniqueId(), price)) return "You cannot afford this item.";

        // Refund current bidder if any
        if (listing.hasBid()) refundBidder(listing.getCurrentBidder(), listing.getCurrentBid(), listing.getCurrency());

        eco.withdraw(buyer.getUniqueId(), price);
        completeSale(listing, buyer.getUniqueId(), price, eco);

        return null;
    }

    // ── Collect ───────────────────────────────────────────────────────────────

    /** Returns items and funds queued for collection to the player. */
    public void collect(Player player) {
        UUID uuid = player.getUniqueId();

        List<ItemStack> items = collectItems.remove(uuid);
        if (items != null) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(items.toArray(new ItemStack[0]));
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }

        Map<String, Double> funds = collectFunds.remove(uuid);
        if (funds != null) {
            for (Map.Entry<String, Double> entry : funds.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) continue;
                EconomyProvider eco = plugin.getEconomyRegistry().get(entry.getKey());
                if (eco == null) eco = plugin.getEconomyRegistry().getDefault();
                if (eco != null) eco.deposit(uuid, entry.getValue());
            }
        }

        if (items != null || funds != null) {
            plugin.getMessageManager().send(player, "auction.collect-success");
        } else {
            plugin.getMessageManager().send(player, "auction.collect-empty");
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Returns all active listings (from cache). */
    public List<AuctionListing> getActiveListings() {
        return cache.values().stream()
            .filter(AuctionListing::isActive)
            .sorted(Comparator.comparing(AuctionListing::getCreatedAt).reversed())
            .toList();
    }

    /** Returns a listing's real-time data from cache. */
    public AuctionListing getCached(long id) {
        return cache.get(id);
    }

    /** Returns all listings for a specific seller. */
    public CompletableFuture<List<AuctionListing>> getSellerListings(UUID sellerUuid) {
        return repo.getBySeller(sellerUuid);
    }

    public boolean hasItemsToCollect(UUID uuid) {
        if (collectItems.containsKey(uuid)) return true;
        Map<String, Double> funds = collectFunds.get(uuid);
        return funds != null && funds.values().stream().anyMatch(v -> v != null && v > 0);
    }

    /**
     * Cancels a listing by the seller. Only allowed when no bids have been placed.
     *
     * @return null on success, or an error message
     */
    public String cancelListing(Player seller, long listingId) {
        AuctionListing listing = cache.get(listingId);
        if (listing == null || !listing.isActive()) return "Listing not found or already expired.";
        if (!listing.getSellerUuid().equals(seller.getUniqueId())) return "You do not own this listing.";
        if (listing.hasBid()) return "You cannot cancel a listing that has active bids.";

        listing.setStatus(AuctionListing.Status.EXPIRED);
        repo.updateStatus(listingId, AuctionListing.Status.EXPIRED);
        cache.remove(listingId);
        queueItemForCollect(seller.getUniqueId(), listing.getItem());
        return null;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void processExpired() {
        repo.getExpired().thenAccept(expired -> {
            if (expired == null) return; // DB error — skip this cycle
            for (AuctionListing listing : expired) {
                if (listing.hasBid()) {
                    // Sell to current bidder
                    EconomyProvider expiredEco = plugin.getEconomyRegistry().get(listing.getCurrency());
                    if (expiredEco == null) expiredEco = plugin.getEconomyRegistry().getDefault();
                    if (expiredEco == null) {
                        plugin.getLogger().warning("[AuctionService] No economy provider for currency '"
                            + listing.getCurrency() + "' — seller " + listing.getSellerName()
                            + " was not paid for listing #" + listing.getId());
                    }
                    completeSale(listing, listing.getCurrentBidder(), listing.getCurrentBid(), expiredEco);
                } else {
                    // No bids — return item to seller
                    listing.setStatus(AuctionListing.Status.EXPIRED);
                    repo.updateStatus(listing.getId(), AuctionListing.Status.EXPIRED);
                    cache.remove(listing.getId());
                    queueItemForCollect(listing.getSellerUuid(), listing.getItem());

                    // Notification must run on the main thread (async expiry scheduler)
                    String iName = listing.getItem() != null ? listing.getItem().getType().name() : "Item";
                    UUID sellerUuid = listing.getSellerUuid();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player seller = Bukkit.getPlayer(sellerUuid);
                        if (seller != null) {
                            plugin.getMessageManager().send(seller, "auction.expired-seller", "item", iName);
                        }
                    });
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Error processing expired auction listings", ex);
            return null;
        });
    }

    private void completeSale(AuctionListing listing, UUID buyerUuid, double price, EconomyProvider eco) {
        listing.setStatus(AuctionListing.Status.SOLD);
        repo.updateStatus(listing.getId(), AuctionListing.Status.SOLD);
        cache.remove(listing.getId());

        // Pay seller (minus fee) — economy deposit is generally thread-safe
        double fee    = price * (getAuctionFeePercent() / 100.0);
        double payout = price - fee;
        if (eco != null) eco.deposit(listing.getSellerUuid(), payout);

        // Queue item for buyer (ConcurrentHashMap — thread-safe)
        queueItemForCollect(buyerUuid, listing.getItem());

        // Resolve display names before scheduling (getPlayer() is safe from any thread)
        Player buyerOnline = Bukkit.getPlayer(buyerUuid);
        String buyerName   = buyerOnline != null ? buyerOnline.getName() : buyerUuid.toString();
        plugin.getDiscordLogger().logAuctionSold(listing, buyerName, price);

        // All Bukkit API calls (event fire, sendMessage, playSound, spawnParticle) must
        // run on the main thread — this method may be called from an async expiry scheduler.
        AuctionSoldEvent event   = new AuctionSoldEvent(listing, buyerUuid, price);
        String itemName          = listing.getItem() != null ? listing.getItem().getType().name() : "Item";
        String priceStr          = eco != null ? eco.format(payout) : String.format("%.2f", payout);
        String currencyName      = eco != null ? eco.getCurrencyName() : "";

        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getPluginManager().callEvent(event);

            Player seller = Bukkit.getPlayer(listing.getSellerUuid());
            Player buyer  = Bukkit.getPlayer(buyerUuid);
            if (seller != null) {
                plugin.getMessageManager().send(seller, "auction.sold",
                    "item", itemName, "price", priceStr, "currency", currencyName);
                plugin.getSoundManager().play(seller, "sell-success");
            }
            if (buyer != null) {
                plugin.getMessageManager().send(buyer, "auction.won",
                    "item", itemName, "price", priceStr, "currency", currencyName);
                plugin.getSoundManager().play(buyer, "auction-win");
                plugin.getParticleManager().spawn(buyer, "auction-win");
            }
        });
    }

    private void refundBidder(UUID bidderUuid, double amount, String currencyId) {
        // Queue the currency refund; give directly if online
        EconomyProvider eco = plugin.getEconomyRegistry().get(currencyId);
        Player bidder = Bukkit.getPlayer(bidderUuid);
        if (bidder != null && eco != null) {
            eco.deposit(bidderUuid, amount);
            plugin.getMessageManager().send(bidder, "auction.outbid",
                "item", "the auction", "bid", eco.format(amount), "currency", eco.getCurrencyName());
        } else {
            collectFunds.computeIfAbsent(bidderUuid, k -> new ConcurrentHashMap<>())
                .merge(currencyId != null ? currencyId : "default", amount, Double::sum);
        }
    }

    private void queueItemForCollect(UUID uuid, ItemStack item) {
        if (item == null) return;
        collectItems.computeIfAbsent(uuid, k -> new ArrayList<>()).add(item.clone());
    }
}
