# FluxShop — Complete Project Reference

> Everything about the project in one place: architecture, integrations, configuration, API, storage, and contributing.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Repository Layout](#2-repository-layout)
3. [Build System](#3-build-system)
4. [Architecture](#4-architecture)
5. [Configuration Files](#5-configuration-files)
6. [Economy System](#6-economy-system)
7. [Shop Engine](#7-shop-engine)
8. [GUI Framework](#8-gui-framework)
9. [Feature Modules](#9-feature-modules)
   - 9.1 [Auction House](#91-auction-house)
   - 9.2 [Player Trade](#92-player-trade)
   - 9.3 [Black Market](#93-black-market)
   - 9.4 [Analytics Dashboard](#94-analytics-dashboard)
10. [Storage Layer](#10-storage-layer)
11. [Plugin Integrations](#11-plugin-integrations)
    - 11.1 [Economy Providers](#111-economy-providers)
    - 11.2 [Spawner Plugins](#112-spawner-plugins)
    - 11.3 [Custom Item Plugins](#113-custom-item-plugins)
    - 11.4 [NPC Plugins](#114-npc-plugins)
    - 11.5 [Utility Integrations](#115-utility-integrations)
12. [NMS & Version Compatibility](#12-nms--version-compatibility)
13. [Commands & Permissions](#13-commands--permissions)
14. [PlaceholderAPI Expansion](#14-placeholderapi-expansion)
15. [Developer API](#15-developer-api)
16. [Default Survival Content](#16-default-survival-content)
17. [Testing](#17-testing)
18. [Known Limitations](#18-known-limitations)
19. [Contributing](#19-contributing)

---

## 1. Project Overview

FluxShop is a **standalone Spigot/Paper shop plugin** targeting server versions **1.8.8 through 1.21.x**. It is designed to match and surpass EconomyShopGUI in every area while adding exclusive features not found in other shop plugins.

| Feature set | Details |
|-------------|---------|
| **Core shop** | Multi-shop YAML configs, paginated animated GUIs, per-item buy/sell pricing, global + per-player stock, on-buy/sell console commands, permission-gated items, purchase cooldown |
| **Auction House** | Full bidding lifecycle, buy-now, anti-snipe timer, listing fees, collect flow, Discord logging |
| **Player Trades** | GUI-based item + currency swap, dual-confirm, atomic rollback, trade history |
| **Black Market** | Rotating configurable item pool, per-player limits, countdown timer, mysterious GUI |
| **Analytics** | Top items/buyers, revenue by currency, economy health score, in-game dashboard, CSV export |
| **Economy** | 6 provider integrations, per-shop/per-item overrides, LuckPerms discount + sell-multiplier groups |
| **Spawner support** | 9 spawner plugins auto-detected at startup |
| **Custom items** | ItemsAdder, Oraxen, MythicMobs — full buy/sell/sellall/black-market support |
| **NPC shops** | Citizens trait, ZNPCsPlus action |
| **Bedrock** | GeyserMC/Floodgate detection, GUI resize, SellGUI disable |
| **Performance** | HikariCP pool, async CompletableFuture DB, Caffeine in-memory cache, single-thread serialized writes |

**Author:** sneakywrld
**bStats:** https://bstats.org/plugin/bukkit/FluxShop/31387
**Target:** Spigot / Paper 1.8.8 – 1.21.x, Java 21+

---

## 2. Repository Layout

```
FluxShop/
├── build.gradle.kts                   # Gradle build — Shadow JAR, maven-publish, all deps
├── settings.gradle.kts
│
├── src/main/java/dev/fluxshop/
│   ├── FluxShopPlugin.java            # Main plugin class — wires all services
│   ├── api/                           # Public developer API (published as :api classifier)
│   │   ├── FluxShopAPI.java           # Static service locator
│   │   └── event/                     # 8 cancellable/informational events
│   ├── auction/
│   │   └── AuctionService.java        # Listing lifecycle, bidding, expiry, collect
│   ├── blackmarket/
│   │   └── BlackMarketService.java    # Rotation scheduling, purchase flow, pool loading
│   ├── command/
│   │   └── CommandManager.java        # Registers all commands; delegates to sub-handlers
│   ├── compat/
│   │   ├── CompatManager.java         # Soft-depend hooks (ItemsAdder, Oraxen, MythicMobs,
│   │   │                              #   WorldGuard, GeyserMC, RealisticSeasons, ...)
│   │   ├── DiscordLogger.java         # DiscordSRV webhook logging
│   │   ├── FluxShopExpansion.java     # PlaceholderAPI expansion (21 placeholders)
│   │   ├── RealisticSeasonsListener.java
│   │   ├── citizens/                  # Citizens NPC trait
│   │   ├── spawner/                   # 9 spawner provider implementations
│   │   └── znpcs/                     # ZNPCsPlus action + listener
│   ├── config/
│   │   ├── ConfigManager.java         # Loads/reloads config.yml + all GUI YAMLs
│   │   └── MessageManager.java        # i18n, MiniMessage + legacy color, PAPI support
│   ├── economy/                       # 6 economy provider implementations + registry
│   ├── gui/                           # 14 GUI implementations + GuiBuilder + GuiManager
│   ├── listener/                      # GuiListener, PlayerListener, ShopStandListener
│   ├── model/                         # Data models: Shop, ShopItem, AuctionListing, etc.
│   ├── nms/                           # NMS abstraction: Legacy(1.8-1.12), Modern, v1_21
│   ├── shop/                          # ShopManager, ShopLoader, TransactionProcessor,
│   │                                  #   SellAllProcessor, PriceCalculator, StockManager
│   ├── storage/                       # Database + 6 async repositories
│   ├── trade/
│   │   └── TradeManager.java          # Trade session lifecycle
│   └── util/
│       ├── ParticleManager.java       # Version-safe particle effects
│       └── SoundManager.java          # Version-safe sound playback
│
├── src/main/resources/
│   ├── plugin.yml
│   ├── config.yml                     # 427-line master config (all keys documented inline)
│   ├── messages.yml                   # All player-facing strings
│   ├── black_market_pool.yml          # Default Black Market item pool
│   ├── guis/                          # 8 GUI layout configs
│   └── shops/                         # 12 pre-configured survival economy shops
│
└── src/test/java/dev/fluxshop/
    ├── auction/AuctionListingTest.java        (19 tests)
    ├── blackmarket/BlackMarketListingTest.java (14 tests)
    ├── blackmarket/BlackMarketServiceTest.java  (8 tests)
    ├── economy/EconomyRegistryTest.java         (8 tests)
    ├── shop/PriceCalculatorTest.java           (28 tests)
    ├── shop/SellAllProcessorTest.java          (10 tests)
    ├── shop/StockManagerTest.java              (16 tests)
    ├── shop/TradeOfferTest.java                (13 tests)
    ├── shop/TransactionProcessorTest.java      (14 tests)
    ├── storage/DatabaseSchemaTest.java         (10 tests)
    └── storage/PriceModifierRepositoryTest.java (20 tests)
                                               ─────────
                                               160 tests total
```

---

## 3. Build System

**Gradle 8.7 + Kotlin DSL** with the Shadow plugin for fat JAR packaging.

```bash
# Full build (produces build/libs/FluxShop-<version>.jar)
./gradlew shadowJar

# API-only JAR for JitPack (produces build/libs/FluxShop-<version>-api.jar)
./gradlew apiJar

# Run all 160 tests
./gradlew test

# Build everything
./gradlew build
```

### Key dependencies

| Dependency | Scope | Purpose |
|------------|-------|---------|
| `paper-api:1.21.4` | `compileOnly` | Server API (NMS layer handles older versions) |
| `VaultAPI:1.7.1` | `compileOnly` | Economy bridge |
| `placeholderapi:2.11.6` | `compileOnly` | Placeholder expansion |
| `citizens-main:2.0.35` | `compileOnly` | NPC trait |
| `Mythic-Dist:5.7.0` | `compileOnly` | MythicMobs items |
| `discordsrv:1.27.0` | `compileOnly` | Discord logging |
| `luckperms-api:5.4` | `compileOnly` | Discount/multiplier groups |
| `worldguard-bukkit:7.0.9` | `compileOnly` | Region flag |
| `HikariCP:5.1.0` | `implementation` (shaded) | DB connection pool |
| `caffeine:3.1.8` | `implementation` (shaded) | In-memory cache |
| `bstats-bukkit:3.0.2` | `implementation` (shaded) | Plugin metrics |
| `sqlite-jdbc:3.45.3.0` | `testImplementation` | Integration tests |

Shaded packages are relocated under `dev.fluxshop.libs.*` to avoid classpath conflicts with other plugins.

ItemsAdder and Oraxen have **no compile-time dependency** — accessed entirely via reflection at runtime.

---

## 4. Architecture

### Initialization order (`FluxShopPlugin.onEnable`)

```
1. NMS handler detection (VersionUtil → selects Legacy / Modern / v1_21)
2. ConfigManager + MessageManager (load all YAML configs)
3. Database (HikariCP connect, schema migrate)
4. EconomyRegistry + CompatManager (register all soft-depend hooks)
5. SoundManager + ParticleManager
6. GuiManager
7. ShopManager + ShopStandManager + PriceCalculator (load shops from YAML)
8. DiscordLogger, AuctionService, TradeManager, BlackMarketService,
   AnalyticsRepository, PriceModifierRepository
9. CommandManager (register all commands)
10. Listeners (GuiListener, PlayerListener, ShopStandListener)
11. FluxShopAPI.init() (expose static service locator)
12. Metrics (bStats, ID 31387)
```

### Threading model

- **Main thread** — all Bukkit API calls (inventory manipulation, event dispatch, player messages)
- **DB thread** — single `Executors.newSingleThreadExecutor()` serializes all database operations; prevents SQLite WAL conflicts and MySQL race conditions
- **Scheduler** — `runTaskTimerAsynchronously` for rotation checks and cache refresh; results are applied via `thenAccept` on the DB thread or `runTask` back to main thread where required

### Key design patterns

| Pattern | Where used |
|---------|-----------|
| Repository pattern | `TransactionRepository`, `StockRepository`, `AuctionRepository`, `BlackMarketRepository`, `TradeRepository`, `AnalyticsRepository`, `PriceModifierRepository` |
| Provider chain | `EconomyRegistry` — Flux → Vault → CMI → Essentials → PlayerPoints → CoinsEngine |
| Reflection-based soft-depend | All integrations without compile-time deps (ItemsAdder, Oraxen, ZNPCsPlus, RealisticSeasons, all spawner plugins) |
| Cancellable event | All 5 cancellable events allow other plugins to modify price or block transactions |
| Cache-aside | `PriceModifierRepository` (Caffeine), `FluxShopExpansion` analytics cache (5 min TTL), discount cache (30 s TTL) |

---

## 5. Configuration Files

### `config.yml` (427 lines)

Every key is documented inline. Major sections:

| Section | Key settings |
|---------|-------------|
| `general` | `locale`, `abbreviate-numbers`, `banned-gamemodes`, `disabled-worlds`, `metrics` |
| `database` | `type` (sqlite/mysql), `host`, `port`, `name`, `user`, `password`, HikariCP pool tuning |
| `economy` | `default-provider`, `log-transactions` |
| `shop` | `purchase-cooldown-ticks`, `confirm-purchase`, `sellall-sort` (PRICE_HIGH/PRICE_LOW/NONE), `spawner-provider` |
| `dynamic-pricing` | `enabled`, `min-multiplier`, `max-multiplier`, `sensitivity` |
| `discounts.groups` | `vip: 5`, `mvp: 10`, `elite: 15` (buy discount %) |
| `sell-multipliers.groups` | `vip: 1.05`, `mvp: 1.10`, `elite: 1.15` |
| `auction` | `listing-fee-percent`, `max-listings-per-player`, `durations`, `anti-snipe-seconds`, `retention-days` |
| `trade` | `request-timeout-seconds`, `cooldown-seconds`, `allow-currency-trading` |
| `black-market` | `items-per-rotation`, `max-purchases-per-player`, `announce-rotation` |
| `analytics` | `history-days`, `aggregation-interval-minutes` |
| `sounds` | Per-event sound keys (shop_open, purchase_success, sell_success, …) |
| `particles` | Per-event particle keys with offset-x/y/z, count, speed |
| `discord` | `enabled`, `channel`, per-event toggles |
| `bedrock` | `resize-guis`, `disable-sell-gui` |
| `realistic-seasons` | `enabled`, `announce-change`, `change-message`, per-season price multipliers |
| `worldguard` | `enabled`, flag enforcement |

### `messages.yml` (152 lines)

All player-facing strings. Supports:
- **MiniMessage** tags (`<red>`, `<gradient:...>`, `<bold>`, etc.)
- **Legacy color codes** (`&a`, `&l`, etc.) — converted via `ChatColor.translateAlternateColorCodes`
- **PlaceholderAPI** — parsed when PAPI is present
- **Variable substitution** — `{player}`, `{amount}`, `{price}`, `{currency}`, `{item}`, etc.

### Shop YAML format

```yaml
id: myshop
display-name: "&6My Shop"
permission: ""           # empty = no permission required
allowed-worlds: []       # empty = all worlds
blocked-worlds: []
icon:
  material: CHEST

categories:
  tools:
    display-name: "&eBTools"
    icon:
      material: IRON_PICKAXE
    economy-provider: vault    # override economy for this category
    items:
      iron_pickaxe:
        material: IRON_PICKAXE
        display-name: "&fIron Pickaxe"
        buy-price: 150.0
        sell-price: 60.0
        global-stock: 500
        player-limit: 5
        auto-restock-seconds: 3600
        buy-permission: "myserver.tools"
        on-buy-commands:
          - "give {player} iron_pickaxe {amount}"
        economy-provider: playerpoints
        dynamic-pricing: true
        season-modifiers:
          WINTER: 1.3
          SUMMER: 0.9
        slot: 0

      custom_sword:
        mythic-item: MythicSword
        buy-price: 500.0
        sell-price: 200.0

      ruby_gem:
        items-adder: mynamespace:ruby_gem
        buy-price: 300.0
        sell-price: 120.0

      magic_staff:
        oraxen: magic_staff
        buy-price: 800.0
        sell-price: -1      # not sellable
```

---

## 6. Economy System

### Provider priority (auto-detected at startup)

```
Flux → Vault → CMI → EssentialsX → PlayerPoints → CoinsEngine
```

The first available provider becomes the default. Any provider can be overridden at the shop, category, or item level via `economy-provider: <id>`.

### Provider IDs

| Plugin | Provider ID |
|--------|------------|
| Flux | `flux` |
| Vault | `vault` |
| CMI | `cmi` |
| EssentialsX | `essentials` |
| PlayerPoints | `playerpoints` |
| CoinsEngine | `coinsengine` |

### LuckPerms discount groups

Defined in `config.yml`:
```yaml
discounts:
  enabled: true
  groups:
    vip: 5        # 5% buy discount for players with fluxshop.discount.vip
    mvp: 10
    elite: 15

sell-multipliers:
  enabled: true
  groups:
    vip: 1.05     # 5% sell bonus for players with fluxshop.multiplier.vip
    mvp: 1.10
    elite: 1.15
```

Highest applicable group wins (non-additive). Discounts stack with dynamic pricing and seasonal modifiers.

### Dynamic pricing

When `dynamic-pricing: true` on an item, price adjusts based on recent global purchase volume:

```
finalPrice = basePrice × clamp(demandMultiplier, min-multiplier, max-multiplier)
```

Sensitivity, min, and max are configured globally in `config.yml`.

### Seasonal pricing

Season modifiers are matched against `RealisticSeasons` season name (case-insensitive):
```yaml
season-modifiers:
  SUMMER: 1.20
  WINTER: 0.80
  SPRING: 1.00
  AUTUMN: 0.90
```

---

## 7. Shop Engine

### Loading pipeline

```
ShopManager.loadAll()
  └─ ShopLoader.load(file)
       ├─ Parse shop metadata
       ├─ Parse categories
       └─ Parse items
            ├─ Resolve material / mythic-item / items-adder / oraxen
            ├─ Apply enchantments (vanilla + EcoEnchants / AdvancedEnchantments / ExcellentEnchants / CrazyEnchantments)
            ├─ Apply season-modifiers (keys stored as UPPERCASE)
            └─ Validate buy/sell prices
```

### Transaction flow — Buy

```
TransactionProcessor.buy(player, item, quantity)
  1. Cooldown check (configurable ticks, bypassable with fluxshop.bypass.cooldown)
  2. Permission check (item.buyPermission)
  3. Price calculation (PriceCalculator: base × discount × seasonal × dynamic × modifier)
  4. Fire ShopPurchaseEvent (cancellable; event can modify finalPrice)
  5. Stock checks (StockManager: global stock, per-player limit)
  6. Funds check (economy.has)
  7. Inventory space check (empty slot OR stackable partial slot)
  8. economy.withdraw
  9. player.inventory.addItem (overflow dropped at feet)
 10. Execute on-buy-commands
 11. stockRepo.recordPurchase (async)
 12. txRepo.log + discordLogger.logTransaction (async)
 13. Sound + particles
```

### Transaction flow — Sell

```
TransactionProcessor.sell(player, item, quantity)
  1. Permission check (item.sellPermission)
  2. Price calculation
  3. Count items in player inventory (metadata-aware matching)
  4. Fire ShopSellEvent (cancellable)
  5. economy.deposit (pay BEFORE removing items — no silent item loss)
  6. Remove items from inventory
  7. Execute on-sell-commands
  8. txRepo.log + discordLogger (async)
  9. Sound + particles
```

### Item matching

`itemMatches()` checks in priority order:
1. Material must match (fast pre-check)
2. **MythicMobs** — match by internal `MYTHIC_TYPE` NBT tag
3. **ItemsAdder** — match by `namespace:id` (prevents IA items matching vanilla base material)
4. **Potions** — match by `PotionData.getType()`
5. **Enchanted books** — match by stored enchantment set
6. Everything else — material match is sufficient

### `/sellall` flow

```
SellAllProcessor.sellAll(player, filter)
  1. Build bestPrices map: itemKey → highest sell price across all shops
     - itemKey format: "IA:<ns:id>" | "MATERIAL:POTION_TYPE" | "MATERIAL:enchants" | "MATERIAL"
  2. Sort by config sellall-sort (PRICE_HIGH default)
  3. For each item in bestPrices:
     a. countAndRemove — remove all matching items from inventory
     b. Resolve economy provider
     c. Calculate earned = round(pricePerUnit × count × 100) / 100
     d. economy.deposit — if fails, returnItems (no silent loss)
     e. txRepo.log
  4. Send summary message + sound + particles
```

---

## 8. GUI Framework

All GUIs extend `FluxGui`, which provides:
- Animated icon cycling via repeating scheduler task
- Rainbow border animation (configurable)
- `onOpen(Player)` / `onClose(Player)` / `onClick(Player, slot, clickType)` hooks
- Version-safe title setting via NMS handler

### GuiManager

Tracks all open GUIs in a `Map<Player, FluxGui>`. Handles:
- Back-navigation stack (history preserved across GUI switches)
- `openReplace(player, gui)` — closes current GUI properly before opening new one (calls `onClose` to prevent item loss in SellDropGui)
- `closeAll()` — called on reload and plugin disable

### GUI inventory

| GUI | Size | Purpose |
|-----|------|---------|
| `MainMenuGui` | 54 | Animated shop category browser |
| `CategoryGui` | 54 | Paginated item list with filter/search |
| `BuyGui` | 54 | Quantity selector + confirm |
| `SellGui` | 54 | Sell quantity selector + confirm |
| `SellAllGui` | 54 | Inventory preview with per-item breakdown |
| `SellDropGui` | 54 | Drag-and-drop sell; running total ticker |
| `AdminShopGui` | 54 | Shop/category/item CRUD editor |
| `AuctionHouseGui` | 54 | Browse + filter + bid/buy-now |
| `AuctionCreateGui` | 54 | List item; chat input for price |
| `AuctionBidGui` | 54 | Bid increments + confirm |
| `TradeGui` | 54 | Split-pane item + currency swap |
| `BlackMarketGui` | 54 | Mysterious themed; countdown timer in lore |
| `AnalyticsGui` | 54 | Admin-only stats dashboard |

---

## 9. Feature Modules

### 9.1 Auction House

**Listing lifecycle:**
```
ACTIVE → bid received → still ACTIVE (outbid refund sent async)
       → buy-now clicked → SOLD (winner gets item, seller gets funds)
       → expiry scan → EXPIRED (item returned to seller, bids refunded)
                     → collect flow → item/funds claimed
```

**Anti-snipe:** Any bid placed within `anti-snipe-seconds` (default 30) of expiry automatically extends the listing by that same duration.

**Outbid refunds:** Previous bidder's currency is refunded immediately when outbid. Currency ID is stored alongside the bid amount so the correct economy provider is used even after a plugin reload.

**Listing fee:** Deducted from seller at listing time (`listing-fee-percent` of `start-price`). Non-refundable on expiry.

**Persistence:** All listings survive restarts. Expiry scan runs every 60 seconds via async scheduler.

### 9.2 Player Trade

**Session lifecycle:**
```
/trade <player>  →  request sent (timeout configurable)
target /trade <player>  →  TradeGui opens for both players

Both players add items (3×3 grid each side) + optional currency
Either player confirms  →  other side must also confirm
Any change after confirm  →  both confirms reset
Both confirmed  →  atomic exchange
  ├─ Verify both players still online
  ├─ Verify both inventories have the offered items
  ├─ economy.withdraw(A, currencyA) + economy.withdraw(B, currencyB)
  ├─ inventory swap
  ├─ Fire TradeCompleteEvent
  └─ tradeRepo.log
```

**Anti-scam protection:** The currency offer reset-on-change path explicitly calls `resetConfirmations()` so a player cannot reduce their currency offer to 0 after the counterparty has confirmed.

### 9.3 Black Market

**Rotation:**
- Checks every 60 seconds whether `Instant.now().isAfter(nextRotation)`
- On rotation: clears DB + memory, shuffles pool, selects N items, persists, broadcasts
- Rotation interval: `ROTATION_HOURS = 6` (hardcoded; configurable pool, stock, and limits are in `config.yml`)

**Item pool (`black_market_pool.yml`):**
```yaml
rare_sword:
  items-adder: mynamespace:rare_sword    # priority 1: ItemsAdder
  # oraxen: rare_sword                  # priority 2: Oraxen
  # material: DIAMOND_SWORD             # priority 3: vanilla
  display-name: "&c&lRare Sword"
  price: 5000.0
  currency: vault
  stock: 3
  max-per-player: 1
```

**Default pool:** 14 rare vanilla items (Enchanted Golden Apple, Netherite Ingot, Elytra, Totem of Undying, Nether Star, etc.) used when no `black_market_pool.yml` exists.

### 9.4 Analytics Dashboard

**Data collected:** Every buy/sell writes a `Transaction` row with: uuid, player name, type, shop id, category id, item id, item name, quantity, unit price, currency, timestamp.

**`AnalyticsRepository.loadSnapshot(windowMs, topN)`** runs 6 queries in parallel:
- Top N bought items by quantity
- Top N sold items by quantity
- Revenue by currency (SUM of price × quantity for BUY transactions)
- Total transaction count
- Economy health (buy volume / sell volume ratio)
- Top N buyers by transaction count

Results are assembled into an `AnalyticsSnapshot` record with defensive copies and `generatedAt` timestamp.

**CSV export:** `/fluxshop analytics export` writes `plugins/FluxShop/analytics_export_<timestamp>.csv`.

---

## 10. Storage Layer

### Schema

```sql
fluxshop_transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  uuid TEXT NOT NULL, player_name TEXT,
  type TEXT NOT NULL,              -- BUY | SELL
  shop_id TEXT, category_id TEXT, item_id TEXT, item_name TEXT,
  quantity INTEGER, unit_price REAL, currency TEXT,
  timestamp INTEGER NOT NULL
)

fluxshop_stock (
  shop_id TEXT NOT NULL, item_id TEXT NOT NULL,
  uuid TEXT NOT NULL DEFAULT '__global__',   -- '__global__' = server-wide stock
  sold INTEGER NOT NULL DEFAULT 0,
  reset_at INTEGER,
  PRIMARY KEY (shop_id, item_id, uuid)
)

fluxshop_auction_listings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  seller_uuid TEXT, item_data BLOB, start_price REAL, buy_now REAL,
  current_bid REAL, bidder_uuid TEXT, bidder_currency TEXT,
  expires_at INTEGER, status TEXT              -- ACTIVE | SOLD | EXPIRED
)

fluxshop_trade_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  player_a TEXT, player_b TEXT,
  items_a TEXT, items_b TEXT,                  -- base64-serialized ItemStack arrays
  currency_a REAL, currency_b REAL, currency_id_a TEXT, currency_id_b TEXT,
  completed_at INTEGER
)

fluxshop_blackmarket (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  item_data BLOB, price REAL, currency TEXT,
  stock INTEGER, purchased INTEGER DEFAULT 0,
  max_per_player INTEGER DEFAULT 0,
  next_refresh INTEGER
)

fluxshop_price_modifiers (
  uuid TEXT NOT NULL, item_id TEXT NOT NULL,
  modifier REAL NOT NULL,
  expires_at INTEGER,                          -- NULL = never expires
  PRIMARY KEY (uuid, item_id)
)
```

### Global stock sentinel

Global stock rows use `uuid = '__global__'` (a non-NULL string) as the sentinel value. SQL `NULL` values are treated as distinct by UNIQUE constraints, which prevented `ON CONFLICT`/`ON DUPLICATE KEY UPDATE` from firing — using a literal string fixes this.

### Async pattern

All repositories follow the same pattern:
```java
public CompletableFuture<ResultType> someQuery(...) {
    return database.query(sql, params)
        .thenApply(rs -> { /* map ResultSet → model */ });
}
```

`Database.query()` and `Database.execute()` both submit work to the single-thread executor and return `CompletableFuture`. Callers that need the result on the main thread use `Bukkit.getScheduler().runTask(plugin, () -> {...})` inside `thenAccept`.

---

## 11. Plugin Integrations

### 11.1 Economy Providers

All providers are detected in `EconomyRegistry` at startup. Each implements `EconomyProvider`:

```java
public interface EconomyProvider {
    String getId();
    String getCurrencyName();
    String getCurrencyNamePlural();
    String getCurrencySymbol();
    String format(double amount);
    double getBalance(UUID uuid);
    boolean has(UUID uuid, double amount);
    boolean deposit(UUID uuid, double amount);
    boolean withdraw(UUID uuid, double amount);
}
```

CMI, EssentialsX, PlayerPoints, and CoinsEngine are accessed via reflection (no compile-time dep). Vault uses its standard `RegisteredServiceProvider`. Flux uses `FluxAPI.getEconomy()`.

### 11.2 Spawner Plugins

All implement `SpawnerProvider`. Registered in `CompatManager` at startup; the first available plugin wins unless `shop.spawner-provider` is set explicitly in `config.yml`.

| Plugin | Provider class | Detection |
|--------|---------------|-----------|
| RoseStacker | `RoseStackerProvider` | `Bukkit.getPluginManager().isPluginEnabled("RoseStacker")` |
| WildStacker | `WildStackerProvider` | same pattern |
| SilkSpawners | `SilkSpawnersProvider` | handles both v1 and v2 API via reflection |
| UltimateStacker | `UltimateStackerProvider` | reflection |
| SmartSpawner | `SmartSpawnerProvider` | reflection |
| EpicSpawners | `EpicSpawnersProvider` | reflection |
| MineableSpawners | `MineableSpawnersProvider` | reflection |
| SpawnerMeta | `SpawnerMetaProvider` | NBT tag detection |
| Vanilla | `VanillaSpawnerProvider` | fallback — always available |

### 11.3 Custom Item Plugins

**ItemsAdder** (reflection, supports v2 and v3+):
- `getItemsAdderItem(id)` — `CustomStack.getInstance(id).getItemStack()`
- `getItemsAdderItemId(item)` — `CustomStack.byItemStack(item).getNamespacedID()` (v3+) or `.getId()` (v2 fallback)
- Used in: buy (resolveGiveItem), sell (itemMatches), sellall (itemKey + itemMatches), black market pool loading

**Oraxen** (reflection):
- `getOraxenItem(id)` — `OraxenItems.getItemById(id).build()`

**MythicMobs** (compile-time dep via `Mythic-Dist`):
- `getMythicItem(id)` — generates a fresh item copy on every buy call (so randomised stats/lore are applied correctly)
- `getMythicItemId(item)` — reads `MYTHIC_TYPE` NBT for matching
- Fresh copy is critical: reusing a snapshot from plugin load time would miss per-drop randomised attributes

### 11.4 NPC Plugins

**Citizens** (`ShopNPCTrait`):
- Registered as a Trait via `CitizensAPI.getTraitFactory()`
- `/npc trait fluxshop` or `/npc fluxshop <npcid> <shopid>`
- Right-click fires `NPCRightClickEvent`; opens the assigned shop after permission + world checks

**ZNPCsPlus** (`ZNPCsInteractListener`):
- Uses a generic `@EventHandler(Event)` filtered by `event.getClass().getSimpleName().equals("NPCInteractEvent")`
- Reads NPC action strings via reflection: tries v4 `getActions()` then v3 `getNpcPojo().getInteractActions()`
- Looks for strings starting with `"FLUXSHOP:"` prefix
- No compile-time dependency — degrades gracefully if ZNPCsPlus is absent

### 11.5 Utility Integrations

**PlaceholderAPI** — `FluxShopExpansion` registers 21 `%fluxshop_*%` placeholders. See [§14](#14-placeholderapi-expansion).

**DiscordSRV** — `DiscordLogger` sends embeds to configured channels on: buy, sell, auction list, auction bid, auction sold, trade complete, black market purchase. Each event type can be independently disabled in `config.yml`.

**LuckPerms** — `PriceCalculator` reads LuckPerms meta node `fluxshop.discount.<group>` and `fluxshop.multiplier.<group>` permissions to apply group-based buy discounts and sell multipliers.

**WorldGuard** — `CompatManager.isShopAllowed(player)` checks the `ALLOW_SHOP` StateFlag via reflection against the player's current region. Denies shop access if the flag is explicitly set to DENY.

**RealisticSeasons** — `RealisticSeasonsListener` listens for `SeasonChangeEvent` via generic event handler, reads season name via reflection, broadcasts a configurable message, and schedules a price recalculation.

**GeyserMC / Floodgate** — `CompatManager.isBedrockPlayer(player)` checks via `FloodgateApi`. If detected: GUIs are resized to 45 slots max; `SellDropGui` (drag-and-drop) is disabled since Bedrock doesn't support it.

---

## 12. NMS & Version Compatibility

Three NMS handler implementations selected at startup by `VersionUtil`:

| Handler | Versions | Approach |
|---------|----------|----------|
| `NMSHandler_Legacy` | 1.8 – 1.12 | Reflection into `net.minecraft.server.<version>.*` |
| `NMSHandler_Modern` | 1.13 – 1.20 | Direct Paper/Spigot API where possible; reflection for NBT |
| `NMSHandler_v1_21` | 1.21+ | Paper Components API; PersistentDataContainer for NBT |

**Interface methods:**

```java
void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut);
String getNBTString(ItemStack item, String key);
ItemStack setNBTString(ItemStack item, String key, String value);
int getCustomModelData(ItemStack item);
String getSkullTexture(ItemStack item);
ItemStack setSkullTexture(ItemStack item, String base64);
String getSpawnerEntityType(ItemStack spawnerItem);
ItemStack setSpawnerEntityType(ItemStack spawnerItem, String entityTypeName);
```

The deprecation warnings in the build output are from `PotionMeta.getBasePotionData()` (deprecated in 1.20.5, flagged for removal). This is intentional — the API is stable 1.9–1.21 and the modern Potion API is not available on legacy versions we still target.

---

## 13. Commands & Permissions

### Command list

| Command | Permission | Default |
|---------|-----------|---------|
| `/shop [section]` | `fluxshop.shop` | true |
| `/sellall [hand\|<material>]` | `fluxshop.sellall` | true |
| `/sellgui` | `fluxshop.sellgui` | true |
| `/auction` | `fluxshop.auction` | true |
| `/auction list` | `fluxshop.auction.list` | true |
| `/trade <player>` | `fluxshop.trade` | true |
| `/blackmarket` | `fluxshop.blackmarket` | op |
| `/fluxshop reload` | `fluxshop.admin.reload` | op |
| `/fluxshop editor` | `fluxshop.admin.editor` | op |
| `/fluxshop give <player> <shopid> <itemid> [amount]` | `fluxshop.admin.give` | op |
| `/fluxshop setprice <shopid> <itemid> <buy\|sell> <price>` | `fluxshop.admin.setprice` | op |
| `/fluxshop setmodifier <player> <itemid> <multiplier> [duration]` | `fluxshop.admin.setmodifier` | op |
| `/fluxshop restock <shopid> [itemid]` | `fluxshop.admin.restock` | op |
| `/fluxshop analytics [export]` | `fluxshop.admin.analytics` | op |
| `/fluxshop import essentials` | `fluxshop.admin.import` | op |
| `/fluxshop export` | `fluxshop.admin.export` | op |

### Permission tree

```
fluxshop.*
├── fluxshop.shop
├── fluxshop.sellall
├── fluxshop.sellgui
├── fluxshop.auction
│   └── fluxshop.auction.list
├── fluxshop.trade
├── fluxshop.blackmarket
├── fluxshop.admin
│   ├── fluxshop.admin.reload
│   ├── fluxshop.admin.editor
│   ├── fluxshop.admin.give
│   ├── fluxshop.admin.setprice
│   ├── fluxshop.admin.setmodifier
│   ├── fluxshop.admin.restock
│   ├── fluxshop.admin.analytics
│   ├── fluxshop.admin.import
│   └── fluxshop.admin.export
├── fluxshop.bypass.stock
└── fluxshop.bypass.cooldown
```

All children are explicitly listed under `fluxshop.*` in `plugin.yml` — Bukkit only traverses one level of inheritance so wildcards must explicitly enumerate all descendants.

---

## 14. PlaceholderAPI Expansion

Identifier: `fluxshop`. Registered when PlaceholderAPI is present.

| Placeholder | Returns | Notes |
|-------------|---------|-------|
| `%fluxshop_balance%` | Formatted balance | Default economy |
| `%fluxshop_balance_raw%` | Raw double | `"%.2f"` format |
| `%fluxshop_balance_<provider>%` | Formatted balance | e.g. `balance_playerpoints` |
| `%fluxshop_currency%` | Currency name | |
| `%fluxshop_currency_plural%` | Currency plural | |
| `%fluxshop_currency_symbol%` | Currency symbol | |
| `%fluxshop_discount%` | Buy discount % | Cached 30 s |
| `%fluxshop_sell_multiplier%` | Sell multiplier | Cached 30 s |
| `%fluxshop_shop_count%` | Loaded shop count | |
| `%fluxshop_shop_<id>_item_count%` | Item count in shop | |
| `%fluxshop_shop_<id>_categories%` | Category count | |
| `%fluxshop_auction_count%` | Active listings | |
| `%fluxshop_player_auction_count%` | Player's listings | |
| `%fluxshop_has_collect%` | `true`/`false` | Has items/funds to collect |
| `%fluxshop_blackmarket_time%` | `HH:MM:SS` until rotation | |
| `%fluxshop_blackmarket_items%` | Current BM listing count | |
| `%fluxshop_in_trade%` | `true`/`false` | |
| `%fluxshop_top_item%` | Top purchased item (24 h) | Cached 5 min |
| `%fluxshop_top_buyer%` | Top buyer name (24 h) | Cached 5 min |
| `%fluxshop_transactions_24h%` | Transaction count (24 h) | Cached 5 min |
| `%fluxshop_version%` | Plugin version string | |

---

## 15. Developer API

### Adding as a dependency

**Gradle (Kotlin DSL):**
```kotlin
repositories { maven("https://jitpack.io") }
dependencies { compileOnly("com.github.sneakywrld:FluxShop:1.0.1:api") }
```

**Maven:**
```xml
<repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
<dependency>
    <groupId>com.github.sneakywrld</groupId>
    <artifactId>fluxshop-api</artifactId>
    <version>1.0.1</version>
    <scope>provided</scope>
</dependency>
```

The `:api` classifier JAR contains only `dev.fluxshop.api.**` — 8 event classes and `FluxShopAPI`. No HikariCP, no Caffeine, no Bukkit shading.

### Accessing services

Always guard API access — FluxShop may not be installed:

```java
if (Bukkit.getPluginManager().isPluginEnabled("FluxShop")) {
    ShopManager shops      = FluxShopAPI.getShopManager();
    AuctionService auction = FluxShopAPI.getAuctionService();
    TradeManager trades    = FluxShopAPI.getTradeManager();
    BlackMarketService bm  = FluxShopAPI.getBlackMarketService();
    AnalyticsRepository analytics = FluxShopAPI.getAnalytics();
    EconomyRegistry eco    = FluxShopAPI.getEconomyRegistry();
}
```

### Events

All events are in `dev.fluxshop.api.event`. Cancellable events let you modify the final price or block the action entirely.

```java
// Block purchases above $10,000 for non-VIP players
@EventHandler
public void onPurchase(ShopPurchaseEvent event) {
    if (event.getFinalPrice() > 10_000 && !event.getPlayer().hasPermission("myserver.vip")) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cVIP only!");
    }
}

// Apply a 20% discount for players in a specific world
@EventHandler
public void onPurchase(ShopPurchaseEvent event) {
    if (event.getPlayer().getWorld().getName().equals("creative")) {
        event.setFinalPrice(event.getFinalPrice() * 0.80);
    }
}

// Log every auction sale to your own system
@EventHandler
public void onAuctionSold(AuctionSoldEvent event) {
    myLogger.log(event.getBuyer().getName() + " bought "
        + event.getListing().getItem().getType()
        + " for " + event.getFinalPrice());
}
```

| Event | Cancellable | Key fields |
|-------|-------------|-----------|
| `ShopPurchaseEvent` | Yes | `player`, `item`, `quantity`, `finalPrice` (mutable), `economyId` |
| `ShopSellEvent` | Yes | `player`, `item`, `quantity`, `finalPrice` (mutable) |
| `ShopOpenEvent` | Yes | `player`, `shop` |
| `AuctionListEvent` | Yes | `seller`, `listing`, `startPrice` |
| `AuctionBidEvent` | Yes | `bidder`, `listing`, `bidAmount` |
| `AuctionSoldEvent` | No | `buyer`, `seller`, `listing`, `finalPrice` |
| `TradeCompleteEvent` | No | `playerA`, `playerB`, `itemsA`, `itemsB`, `currencyA`, `currencyB` |
| `BlackMarketPurchaseEvent` | Yes | `buyer`, `listing`, `price` (mutable) |

### Thread safety

- `FluxShopAPI` methods are safe to call from any thread.
- Event handlers fire on the **main thread**.
- Repository `CompletableFuture` results complete on the **DB thread** — use `Bukkit.getScheduler().runTask()` if you need to interact with Bukkit API in the callback.

---

## 16. Default Survival Content

12 shops ship with the plugin covering a complete survival economy:

| Shop | Items | Price range |
|------|-------|-------------|
| `survival_blocks` | 50+ blocks (stone, wood, glass, concrete, wool, ores, terracotta) | $1 – $500 |
| `survival_food` | Crops, meats, fish, golden foods, cake | $2 – $80 |
| `survival_tools` | Wood → Netherite tools + misc (shears, rod, bucket) | $10 – $2,000 |
| `survival_armor` | Leather (colored) → Netherite + elytra, shields | $20 – $6,000 |
| `survival_potions` | Regular + splash: speed, strength, healing, fire res, etc. | $30 – $150 |
| `survival_mob_drops` | Common, nether, end, and rare mob drops | $5 – $500 |
| `survival_enchanting` | Books, lapis, enchanted books (top enchants) | $10 – $2,000 |
| `survival_farming` | Seeds, saplings, dyes, flowers, bonemeal | $1 – $20 |
| `survival_mining` | TNT, rails, pistons, hoppers, redstone components | $5 – $300 |
| `survival_spawners` | Zombie → Elder Guardian spawners | $5,000 – $50,000 |
| `survival_nether` | Nether blocks, resources, ancient debris | $5 – $2,000 |
| `survival_end` | End stone, purpur, chorus, shulker shells, elytra | $10 – $5,000 |

**Economy ratios:** Sell prices are 40–60% of buy prices to prevent exploit loops. Spawner prices are balanced for a mature survival economy.

---

## 17. Testing

**160 tests — 0 failing.**

```bash
./gradlew test
# Test report: build/reports/tests/test/index.html
```

### Test breakdown

| Test class | Count | Type | What's tested |
|------------|-------|------|---------------|
| `AuctionListingTest` | 19 | Unit | Listing state machine, bid increment, expiry, buy-now |
| `BlackMarketListingTest` | 14 | Unit | Stock tracking, per-player limits, remaining stock clamping |
| `BlackMarketServiceTest` | 8 | Unit | Purchase flow: stock, funds, player limit, quantity checks |
| `EconomyRegistryTest` | 8 | Unit | Provider registration, fallback chain, get() edge cases |
| `PriceCalculatorTest` | 28 | Unit | Base price, discount, sell multiplier, seasonal, dynamic, modifier stacking |
| `SellAllProcessorTest` | 10 | Unit | SellResult record, best-price selection, sort order, earnings rounding |
| `StockManagerTest` | 16 | Unit | Global stock, per-player limit, unlimited stock (-1), bypass permission |
| `TradeOfferTest` | 13 | Unit | Trade session state, confirmation logic, reset-on-change |
| `TransactionProcessorTest` | 14 | Unit | Buy/sell result codes: permission, stock, funds, inventory, economy error |
| `DatabaseSchemaTest` | 10 | Integration | Real SQLite — schema creation, all tables, all columns, migration idempotency |
| `PriceModifierRepositoryTest` | 20 | Integration | Real SQLite — set/get/remove/expire/evict modifier lifecycle |

### Integration test notes

`DatabaseSchemaTest` and `PriceModifierRepositoryTest` use JUnit 5 `@TempDir` with a real SQLite database. On Windows, HikariCP holds a file lock on the `.db` file — tests explicitly call `db.disconnect()` before the `@TempDir` cleanup runs to release the lock.

---

## 18. Known Limitations

These are design limitations intentionally left as-is:

| Issue | Where | Why not fixed |
|-------|-------|---------------|
| `StockManager.canBuy()` blocks the calling thread with `.join()` | `StockManager.java` | Stock check must be synchronous to prevent TOCTOU race; full async refactor would require rearchitecting the entire buy flow |
| Trade messages use hardcoded `sendRaw` strings | `TradeCommand.java` | Minor UX issue; dead keys exist in `messages.yml` — not a correctness bug |
| `ROTATION_HOURS = 6` is hardcoded | `BlackMarketService.java` | Cron-style scheduling is on the roadmap; current interval is fixed at compile time |

---

## 19. Contributing

### Setup

```bash
git clone https://github.com/sneakywrld/FluxShop
cd FluxShop
./gradlew build   # downloads deps, compiles, runs all tests
```

JDK 21+ is required. The Flux API dependency (`../Flux/flux-api/build/libs/flux-api-1.0.0.jar`) is optional — remove the `compileOnly(files(...))` line in `build.gradle.kts` if you don't have it locally.

### Code style

- Standard Java conventions; 4-space indentation
- All public methods on service classes have Javadoc
- Repository methods always return `CompletableFuture` — never block the main thread
- All Bukkit API calls must be on the main thread
- New integrations must use `catch (Throwable)` (not `catch (Exception)`) in soft-depend initialization to catch `NoClassDefFoundError`

### Adding a new economy provider

1. Implement `EconomyProvider` in `src/main/java/dev/fluxshop/economy/`
2. Register in `EconomyRegistry.registerAll()` with a `try { ... } catch (Throwable)` guard
3. Add the provider ID to `README.md` and `GITHUB.md` tables

### Adding a new spawner plugin

1. Implement `SpawnerProvider` in `src/main/java/dev/fluxshop/compat/spawner/`
2. Register in `CompatManager.registerAll()` with plugin detection guard
3. Add to the spawner table in `GITHUB.md`

### Running a specific test class

```bash
./gradlew test --tests "dev.fluxshop.shop.PriceCalculatorTest"
```

### Pull request checklist

- [ ] All 160 existing tests pass (`./gradlew test`)
- [ ] New behaviour is covered by at least one test
- [ ] No `TODO`/`FIXME` left in submitted code
- [ ] `CHANGELOG.md` entry added under `[Unreleased]`
- [ ] Javadoc on any new public methods
