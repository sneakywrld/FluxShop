# FluxShop Developer API

This document covers everything an external plugin developer needs to integrate with FluxShop.

---

## Setup

### 1. Soft-depend

In your `plugin.yml`:

```yaml
softdepend: [FluxShop]
```

Always use `softdepend`, not `depend` — your plugin must work even when FluxShop is absent.

### 2. Add to build

The API module is published to JitPack. It contains only the event classes and `FluxShopAPI` facade — no shaded dependencies.

**Gradle (Kotlin DSL):**
```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.sneakywrld:FluxShop:1.0.1:api")
}
```

**Maven:**
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.sneakywrld</groupId>
        <artifactId>fluxshop-api</artifactId>
        <version>1.0.1</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

Alternatively, drop `FluxShop-<version>.jar` into a local `libs/` folder and use:
```kotlin
compileOnly(files("libs/FluxShop.jar"))
```

### 3. Guard every API call

```java
import dev.fluxshop.api.FluxShopAPI;

// Always check availability first
if (!FluxShopAPI.isAvailable()) return;

// Now safe to use
ShopManager shops = FluxShopAPI.getShopManager();
```

`FluxShopAPI.isAvailable()` returns `false` if FluxShop is not installed, not enabled, or has been disabled. It is `true` once `FluxShopPlugin.onEnable()` completes.

---

## API Entry Points

```java
FluxShopAPI.getShopManager()        // → ShopManager
FluxShopAPI.getEconomyRegistry()    // → EconomyRegistry
FluxShopAPI.getAuctionService()     // → AuctionService
FluxShopAPI.getTradeManager()       // → TradeManager
FluxShopAPI.getBlackMarketService() // → BlackMarketService
FluxShopAPI.getAnalytics()          // → AnalyticsRepository
FluxShopAPI.getPlugin()             // → FluxShopPlugin (raw access)
```

---

## ShopManager

Browse and query the shop registry.

```java
ShopManager shops = FluxShopAPI.getShopManager();

// Get a specific shop by id
Optional<Shop> shopOpt = shops.getShop("survival_blocks");
shopOpt.ifPresent(shop -> {
    String name = shop.getDisplayName();
    List<ShopCategory> categories = shop.getCategories();
});

// List all shops
for (Shop shop : shops.getShops()) {
    // ...
}

// Get a specific item from a shop category
shops.getShop("survival_food").ifPresent(shop -> {
    shop.getCategory("meat").ifPresent(category -> {
        category.getItems().stream()
            .filter(item -> item.getMaterial() == Material.COOKED_BEEF)
            .findFirst()
            .ifPresent(item -> {
                double buyPrice  = item.getBuyPrice();  // -1 if not buyable
                double sellPrice = item.getSellPrice(); // -1 if not sellable
            });
    });
});
```

---

## EconomyRegistry

Access economy providers and player balances.

```java
EconomyRegistry registry = FluxShopAPI.getEconomyRegistry();

// Get the default economy provider
EconomyProvider eco = registry.getDefault();
if (eco != null) {
    double balance = eco.getBalance(player.getUniqueId());
    boolean hasEnough = eco.has(player.getUniqueId(), 500.0);
    eco.withdraw(player.getUniqueId(), 100.0);
    eco.deposit(player.getUniqueId(), 50.0);
    String formatted = eco.format(balance); // e.g. "$1,234.00"
}

// Get a specific provider by id
EconomyProvider points = registry.get("playerpoints");
if (points != null) {
    double pts = points.getBalance(player.getUniqueId());
}

// Check if any economy is available
if (!registry.hasProvider()) {
    // No economy plugin found
}
```

---

## AuctionService

List items, place bids, and process expiry.

```java
AuctionService auction = FluxShopAPI.getAuctionService();

// List an item for auction (returns CompletableFuture<String> — null = success, String = error message)
ItemStack item = player.getInventory().getItemInMainHand();
auction.listItem(player, item, 100.0, 500.0, 24, "vault")
    .thenAccept(error -> {
        if (error != null) {
            player.sendMessage("Failed to list: " + error);
        } else {
            player.sendMessage("Listed successfully!");
        }
    });

// Get all active listings
auction.getActiveListings().thenAccept(listings -> {
    for (AuctionListing listing : listings) {
        UUID seller   = listing.getSellerUuid();
        double bid    = listing.getCurrentBid();
        long expiresAt = listing.getExpiresAt(); // epoch ms
        boolean hasBid = listing.hasBid();
    }
});

// Place a bid
auction.placeBid(player, listingId, bidAmount)
    .thenAccept(error -> { /* null = success */ });

// Instant buy-now purchase
auction.buyNow(player, listingId)
    .thenAccept(error -> { /* null = success */ });
```

---

## TradeManager

Initiate and monitor player trades.

```java
TradeManager trades = FluxShopAPI.getTradeManager();

// Send a trade request from one player to another
trades.sendRequest(senderPlayer, targetPlayer);

// Accept a pending request
trades.acceptRequest(targetPlayer);

// Decline a pending request
trades.declineRequest(targetPlayer);

// Check if a player has an active trade
boolean inTrade = trades.isTrading(player.getUniqueId());

// Check if a player has a pending incoming request
boolean hasPending = trades.hasPendingRequest(player.getUniqueId());
```

---

## BlackMarketService

Query and purchase Black Market listings.

```java
BlackMarketService bm = FluxShopAPI.getBlackMarketService();

// Get active Black Market listings
bm.getActiveListings().thenAccept(listings -> {
    for (BlackMarketListing listing : listings) {
        ItemStack item     = listing.getItem();
        double price       = listing.getPrice();
        int remaining      = listing.getRemainingStock();
        boolean inStock    = listing.isInStock();
        long nextRefresh   = listing.getNextRefresh(); // epoch ms
    }
});

// Purchase (returns CompletableFuture<String> — null = success, String = error)
bm.purchase(player, listingId, quantity)
    .thenAccept(error -> { /* null = success */ });
```

---

## AnalyticsRepository

Query aggregated shop statistics asynchronously.

```java
AnalyticsRepository analytics = FluxShopAPI.getAnalytics();

long ONE_DAY = 86_400_000L;

// Load a complete snapshot (all stats in one call)
analytics.loadSnapshot(ONE_DAY, 10).thenAccept(snapshot -> {
    double health = snapshot.economyHealth(); // 0–200
    long txCount  = snapshot.transactionCount();

    for (AnalyticsRepository.ItemStat stat : snapshot.topBought()) {
        String itemId   = stat.itemId();
        long   qty      = stat.quantity();
        double revenue  = stat.revenue();
    }

    snapshot.revenueByCurrency().forEach((currency, total) -> {
        // currency = "vault", "playerpoints", etc.
    });
});

// Individual queries
analytics.getTopBought(ONE_DAY, 5).thenAccept(stats -> { /* ... */ });
analytics.getTopSold(ONE_DAY, 5).thenAccept(stats -> { /* ... */ });
analytics.getTotalRevenue(0).thenAccept(revenueMap -> { /* ... */ }); // 0 = all-time
analytics.getTransactionCount(ONE_DAY).thenAccept(count -> { /* ... */ });
analytics.getEconomyHealthScore(ONE_DAY).thenAccept(score -> { /* ... */ });
analytics.getTopBuyers(ONE_DAY, 5).thenAccept(buyers -> { /* ... */ });
```

---

## Events

All FluxShop events are in the `dev.fluxshop.api.event` package. Register listeners the normal Bukkit way in your plugin's `onEnable`.

### ShopPurchaseEvent — cancellable

Fired before a player buys an item from a shop.

```java
@EventHandler
public void onPurchase(ShopPurchaseEvent event) {
    Player  player   = event.getPlayer();
    ShopItem item    = event.getItem();
    int     quantity = event.getQuantity();
    double  price    = event.getFinalPrice();
    String  economy  = event.getEconomyId();

    // Example: grant loyalty bonus
    if (item.getId().contains("diamond")) {
        player.sendMessage("You earned bonus loyalty points!");
    }

    // Example: block purchase for a specific group
    if (!player.hasPermission("myserver.can_buy_spawners")
            && item.getSpawnerType() != null) {
        event.setCancelled(true);
        player.sendMessage("You need VIP rank to buy spawners.");
        return;
    }

    // Example: apply a custom discount (modifies price)
    if (player.hasPermission("myserver.partner")) {
        event.setFinalPrice(price * 0.85); // 15% partner discount
    }
}
```

### ShopSellEvent — cancellable

Fired before a player sells an item.

```java
@EventHandler
public void onSell(ShopSellEvent event) {
    Player   player   = event.getPlayer();
    ShopItem item     = event.getItem();
    int      quantity = event.getQuantity();
    double   payout   = event.getPayout();

    // Example: apply a sell tax
    if (event.getItem().getCategoryId().equals("spawners")) {
        event.setPayout(payout * 0.9); // 10% sell tax on spawners
    }

    // Example: cancel selling during a specific game state
    if (isArenaActive(player.getWorld())) {
        event.setCancelled(true);
        player.sendMessage("You can't sell during an arena match.");
    }
}
```

### ShopOpenEvent — cancellable

```java
@EventHandler
public void onShopOpen(ShopOpenEvent event) {
    // Block access to a specific shop
    if (event.getShop().getId().equals("endgame_shop")
            && !event.getPlayer().hasPermission("myserver.endgame")) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("Complete the main quest first!");
    }
}
```

### AuctionListEvent — cancellable

```java
@EventHandler
public void onAuctionList(AuctionListEvent event) {
    // Block listing cursed items
    if (event.getItem().getItemMeta() != null
            && event.getItem().getItemMeta().getLore() != null
            && event.getItem().getItemMeta().getLore().contains("§cCursed")) {
        event.setCancelled(true);
        event.getSeller().sendMessage("Cursed items cannot be listed on the auction house.");
    }
}
```

### AuctionBidEvent — cancellable

```java
@EventHandler
public void onBid(AuctionBidEvent event) {
    Player bidder = event.getBidder();
    AuctionListing listing = event.getListing();

    // Example: enforce a minimum bid for VIP auctions
    if (listing.getStartPrice() >= 100_000 && event.getBidAmount() < 1_000) {
        event.setBidAmount(1_000); // raise bid silently
    }

    // Example: log bids to a custom system
    myAuditLog.record(bidder.getUniqueId(), listing.getId(), event.getBidAmount());
}
```

### AuctionSoldEvent — informational

```java
@EventHandler
public void onAuctionSold(AuctionSoldEvent event) {
    AuctionListing listing = event.getListing();
    UUID buyerUuid  = listing.getBidderUuid();
    UUID sellerUuid = listing.getSellerUuid();
    double price    = listing.getCurrentBid();

    // Record to your own economy audit log, etc.
    myLogger.info(sellerUuid + " sold to " + buyerUuid + " for " + price);
}
```

### TradeCompleteEvent — informational

```java
@EventHandler
public void onTradeComplete(TradeCompleteEvent event) {
    TradeOffer offer = event.getOffer();
    UUID playerA = offer.getPlayerA();
    UUID playerB = offer.getPlayerB();

    // Give both players a trade achievement
    achievementManager.award(playerA, "FIRST_TRADE");
    achievementManager.award(playerB, "FIRST_TRADE");
}
```

### BlackMarketPurchaseEvent — cancellable

```java
@EventHandler
public void onBlackMarketPurchase(BlackMarketPurchaseEvent event) {
    // Example: restrict Black Market access to donors
    if (!event.getPlayer().hasPermission("myserver.donor")) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("Donors only!");
    }
}
```

---

## Common Patterns

### Check if a player can afford an item before opening a GUI

```java
if (FluxShopAPI.isAvailable()) {
    FluxShopAPI.getShopManager().getShop("gems").ifPresent(shop ->
        shop.getCategory("diamonds").ifPresent(cat ->
            cat.getItems().stream()
                .filter(i -> i.getId().equals("diamond"))
                .findFirst()
                .ifPresent(item -> {
                    EconomyProvider eco = FluxShopAPI.getEconomyRegistry().getDefault();
                    if (eco != null && !eco.has(player.getUniqueId(), item.getBuyPrice())) {
                        player.sendMessage("You can't afford any diamonds right now.");
                    }
                })
        )
    );
}
```

### React to a purchase and grant a currency reward in a different economy

```java
@EventHandler
public void onPurchase(ShopPurchaseEvent event) {
    if (!"playerpoints".equals(event.getEconomyId())) return;
    if (!FluxShopAPI.isAvailable()) return;

    // Every PlayerPoints purchase also earns 1% back in Vault currency
    EconomyProvider vault = FluxShopAPI.getEconomyRegistry().get("vault");
    if (vault != null) {
        double cashback = event.getFinalPrice() * 0.01;
        vault.deposit(event.getPlayer().getUniqueId(), cashback);
        event.getPlayer().sendMessage("Earned $" + String.format("%.2f", cashback) + " cashback!");
    }
}
```

### Display analytics in a custom scoreboard

```java
FluxShopAPI.getAnalytics().loadSnapshot(86_400_000L, 3).thenAccept(snapshot ->
    Bukkit.getScheduler().runTask(myPlugin, () -> {
        // Must update scoreboard on main thread
        scoreboard.setLine(0, "Economy: " + String.format("%.0f", snapshot.economyHealth()) + "/100");
        scoreboard.setLine(1, "Tx today: " + snapshot.transactionCount());
        if (!snapshot.topBought().isEmpty()) {
            scoreboard.setLine(2, "Top item: " + snapshot.topBought().get(0).itemName());
        }
    })
);
```

---

## Thread Safety

- All `AnalyticsRepository`, `AuctionService`, and `BlackMarketService` methods return `CompletableFuture` — results arrive on the FluxShop DB thread.
- **Never** interact with the Bukkit API (players, inventories, `sendMessage`, etc.) inside a `CompletableFuture` callback without scheduling back to the main thread:

```java
analytics.getTopBought(0, 1).thenAccept(stats ->
    // WRONG — off main thread
    player.sendMessage(stats.get(0).itemName())
);

analytics.getTopBought(0, 1).thenAccept(stats ->
    // CORRECT
    Bukkit.getScheduler().runTask(myPlugin, () ->
        player.sendMessage(stats.get(0).itemName()))
);
```

---

## Version Compatibility

FluxShop targets 1.8.8 – 1.21.x. The API surface is designed to be version-stable; no NMS or version-specific imports are needed in consumer code. All model classes (`ShopItem`, `AuctionListing`, `TradeOffer`, `BlackMarketListing`) use pure Java types and Bukkit's stable API only.
