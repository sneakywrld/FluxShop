# Changelog

All notable changes to FluxShop are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.0.1] — 2026-05-17

### Added

- **Full ItemsAdder support** — buying, selling, `/sellall`, and Black Market pool entries all handle ItemsAdder custom items correctly. `CompatManager` gained `isItemsAdderItem()` and `getItemsAdderItemId()` (supports both IA v2 `getId()` and v3+ `getNamespacedID()` via reflection). `TransactionProcessor.resolveGiveItem()` fetches a fresh IA item on purchase so all NBT tags are guaranteed present. `itemMatches()` in both `TransactionProcessor` and `SellAllProcessor` uses the IA namespace:id for strict identity matching, preventing cross-item grouping (e.g., an IA "ruby sword" no longer matches a vanilla DIAMOND_SWORD). `SellAllProcessor.itemKey()` prefixes IA items with `"IA:"`. `BlackMarketService` gained `resolvePoolItem()` with priority order items-adder → oraxen → material and specific per-failure warnings.
- **ZNPCsPlus integration** — NPC shop action support via reflection (no compile-time dep). Assign via `/znpcs action add <npcid> INTERACT FLUXSHOP <shopid>`. Supports both the ZNPCsPlus v3 pojo API and v4 direct API; degrades gracefully if neither is detected.
- **RealisticSeasons season-change broadcast** — `RealisticSeasonsListener` fires a configurable server-wide message when the season changes. Controlled by `realistic-seasons.announce-change` and `realistic-seasons.change-message` in `config.yml`.
- **`ShopStand` model class** — typed record (`location`, `shopId`) with `fromEntry()` factory and `locationKey()` serializer. `ShopStandManager.getAllStands()` now returns `List<ShopStand>` in addition to the existing `allStands()` entry-set view.
- **`AnalyticsSnapshot` DTO** — typed record bundling all analytics data (topBought, topSold, revenueByCurrency, transactionCount, economyHealth, topBuyers, generatedAt). Defensive copies are made in the compact constructor.
- **`AnalyticsRepository.loadSnapshot(windowMs, topN)`** — single `CompletableFuture<AnalyticsSnapshot>` that runs all six analytics queries in parallel and assembles the result. `AnalyticsGui` simplified to use it.
- **`DatabaseSchemaTest`** — 10 integration tests against a real SQLite database: fresh connect, idempotent reconnect, all tables exist, all required columns present, `max_per_player` migration applies and is idempotent, async query, fire-and-forget insert.
- **`EconomyRegistryTest`** — 8 unit tests: no-provider fallback, `get()` edge cases (null, blank, unknown id), configured-default fallback, `formatDefault()` locale handling (en-US, de-DE, invalid locale).
- SQLite JDBC driver added to `testImplementation` scope for integration tests.

### Fixed

- **Bug 57** (`EconomyRegistry`, `CompatManager`) — `catch (Exception)` in soft-depend initialization didn't catch `NoClassDefFoundError`. Server crashed on startup when Flux was listed in `softdepend` but not installed. Changed to `catch (Throwable)` in all four provider registration locations.
- **Bug 58** (`GuiManager`) — `openReplace()` and `goBack()` updated `openGuis` before calling `onClose()`. `notifyClosed()` couldn't find the GUI by inventory reference, so `SellDropGui.onClose()` (which returns items) was never called on navigation. Added explicit `current.onClose(player)` calls before switching.
- **Bug 59** (`Database`) — `Executors.newFixedThreadPool(4)` allowed concurrent DB execution. `clearAll()` DELETE statements raced against subsequent `insert()` calls in `BlackMarketService.rotate()` on MySQL. Changed to `newSingleThreadExecutor`.
- **Bug 60** (`NMSHandler_Legacy`) — `setNBTString()` used `tag.getClass().getSuperclass()` (resolves to `NBTBase`) to look up the NMS `setTag` method; the actual method signature is `setTag(NBTTagCompound)`. Caused silent `NoSuchMethodException` — NBT tags were never written on 1.8–1.12. Changed to `tag.getClass()`.
- **Bug 61** (`plugin.yml`) — Bukkit permission inheritance only traverses one level. `fluxshop.*` → `fluxshop.admin` did not cascade to `fluxshop.admin.reload` etc. Admins with `fluxshop.*` couldn't use sub-commands. All 10 admin sub-permissions and 2 bypass nodes now explicitly listed under `fluxshop.*` children.
- **Bug 62** (`TradeGui`) — Currency cancel/0 branch didn't call `offer.resetConfirmations()`. A scammer could change their currency offer to 0 after the counterparty confirmed; the peer's confirmation remained active, allowing the trade to execute at 0 cost.
- **Bug 63** (`AuctionCreateGui`) — After chat price input, the GUI was re-opened via `open(player)` directly (bypassing `GuiManager`). The GUI was no longer in `openGuis`, so click events were not routed and items could be freely moved in/out. Both re-open call sites (success and timeout) changed to `plugin.getGuiManager().openReplace(player, AuctionCreateGui.this)`.
- **Bug 55** (`StockRepository`) — Global stock used `uuid = NULL` as the DB sentinel, but SQL treats NULLs as distinct for UNIQUE constraints. `ON CONFLICT`/`ON DUPLICATE KEY UPDATE` never fired for global stock rows, so every purchase inserted a new row instead of updating. Changed sentinel to the non-NULL string `'__global__'`.
- **Bug 54** (`TransactionProcessor`) — `hasInventorySpace()` always used `firstEmpty() != -1` (ignored the item parameter). Stackable purchases were incorrectly rejected with `INVENTORY_FULL` even when the item could stack into a partial slot. Added a secondary scan over `getStorageContents()` for stackable slots.

---

## [1.0.0] — 2026-05-13

### Added

#### Core Shop Engine
- Multi-shop YAML configuration system (`plugins/FluxShop/shops/*.yml`)
- Paginated, animated GUI with configurable border fills and category icons
- Per-item buy price, sell price, global stock, per-player purchase limits
- On-buy and on-sell console command execution with `{player}` and `{amount}` placeholders
- Permission-gated items (`buy-permission`, `sell-permission` per item)
- Configurable purchase cooldown to prevent spam (default: 4 ticks)
- Confirm-purchase dialog (configurable; off by default for sell)
- In-game admin editor GUI for creating/editing shops without touching files

#### Economy Support
- **Flux** — native integration via FluxAPI
- **Vault** — standard service provider bridge
- **CMI** — CMI economy hook
- **EssentialsX** — IEssentials user economy bridge
- **PlayerPoints** — PlayerPointsAPI hook
- **CoinsEngine** — CoinsEngineAPI hook
- Auto-detection priority: Flux → Vault → CMI → Essentials → PlayerPoints → CoinsEngine
- Per-shop and per-item economy provider overrides
- LuckPerms permission group discounts (`fluxshop.discount.<group>`) — configurable %
- LuckPerms permission group sell multipliers (`fluxshop.multiplier.<group>`)

#### Selling
- `/sellall` — sells all eligible items; processes highest-value first by default
- `/sellgui` — drag-and-drop inventory interface for bulk selling
- Buy GUI — quantity selector (×1, ×8, ×16, ×32, ×64, max-affordable, custom input)

#### Dynamic Pricing
- Supply/demand multiplier system with configurable sensitivity, min/max, and recalculation interval
- Per-item dynamic pricing opt-in (`dynamic-pricing: true` in item config)

#### Seasonal Pricing
- RealisticSeasons integration: per-season global multipliers (SPRING, SUMMER, AUTUMN, WINTER)
- Per-item season override (`season-modifiers` map)

#### Auction House
- Full listing lifecycle: create → bid → sold/expired → collect
- Minimum bid increment (configurable %, default 5%)
- Buy-now instant purchase option
- Anti-snipe timer extension (configurable window + extension, default 30s/60s)
- Per-player concurrent listing limit (configurable, default 10)
- Configurable listing fee (% of sale, default 5%)
- Outbid notification to previous bidder with automatic refund
- Seller notification on sale
- Expired listing returns item to seller's collect queue
- Async 60-second expiry processing loop
- Persistent storage in `fluxshop_auction_listings` table

#### Player Trade System
- `/trade <player>` — sends a trade request (configurable 60s timeout)
- Split-pane GUI: player A items left / player B items right
- Currency exchange fields alongside items
- Dual-confirm state machine — any change resets both confirmations
- Final confirmation review before atomic swap
- Atomic rollback on failure (both inventories restored)
- Trade history persisted to `fluxshop_trade_history` table

#### Black Market
- Cron-scheduled rotations (default: every 6 hours at 0:00, 6:00, 12:00, 18:00)
- Configurable item pool (`black_market.yml`) with fallback default pool
- Configurable items per rotation (default: 6 randomly selected)
- Per-player purchase limit per rotation (configurable)
- Countdown timer displayed in GUI lore
- Server-wide rotation broadcast (configurable)
- Atmospheric themed GUI with dark palette

#### Analytics Dashboard
- Top 5 bought items by quantity (24h window, configurable)
- Top 5 sold items by quantity
- Revenue breakdown by currency
- Top buyers by transaction count
- Economy health score (buy/sell ratio, 0–200 scale, color-coded)
- ASCII bar charts in GUI lore
- CSV export via `/fluxshop analytics export`
- Background aggregation on configurable interval (default: 15 minutes)

#### Storage Layer
- SQLite (default) and MySQL backends via HikariCP connection pool
- Async operations throughout via `CompletableFuture`
- Tables: `fluxshop_transactions`, `fluxshop_stock`, `fluxshop_auction_listings`, `fluxshop_trade_history`, `fluxshop_blackmarket`, `fluxshop_price_modifiers`
- Cross-version ItemStack serialization via BukkitObjectStream + Base64

#### Plugin Integrations
- **Citizens** — NPC trait (`@TraitName("fluxshop")`); assign via `/npc fluxshop <npcid> <shopid>`
- **PlaceholderAPI** — `%fluxshop_*%` expansion with 8+ placeholders
- **DiscordSRV** — Reflection-based hook; logs BUY, SELL, AUCTION_LIST, AUCTION_SOLD, AUCTION_BID to configurable channels
- **WorldGuard** — `ALLOW_SHOP` region flag enforcement
- **RealisticSeasons** — Season detection via reflection for price modifiers
- **GeyserMC / Floodgate** — Bedrock player detection; GUI resize to 45 slots; SellGUI disabled for Bedrock players
- **MythicMobs** — Buy/sell MythicMobs items by mob id
- **ItemsAdder** — Custom item support by namespace:id
- **Oraxen** — Custom item support by item id

#### Spawner Plugin Support
- Auto-detection priority: RoseStacker → WildStacker → SilkSpawners → UltimateStacker → SmartSpawner → EpicSpawners → MineableSpawners → SpawnerMeta → Vanilla
- Configurable override in `config.yml` (`shop.spawner-provider`)

#### Sounds & Particles
- Per-event configurable sounds (Bukkit Sound enum, volume, pitch)
- Per-event configurable particles (Bukkit Particle enum, count, offset, speed)
- Events: shop-open, purchase-success, purchase-fail, sell-success, auction-bid, auction-win, trade-complete, blackmarket-open, gui-click, page-turn

#### NMS & Compatibility
- Version-safe NMS handler (1.8.8 → 1.21.x)
- `ItemBuilder` with skull texture, custom model data, NBT, armor trim, colors, banners
- Version-safe particle and sound name resolution

#### Configuration System
- `config.yml` — master config, all keys commented
- `messages.yml` — full i18n, PlaceholderAPI support, MiniMessage + legacy color codes
- `guis/*.yml` — per-GUI layout configuration
- Hot-reload via `/fluxshop reload` (no restart required)

#### Developer API
- `FluxShopAPI` static facade — access all services post-enable
- 8 cancellable/informational events in `dev.fluxshop.api.event`
- All services injectable; shop/auction/trade/blackmarket/analytics all exposed

#### Default Content
- 12 pre-configured survival shops with balanced economy pricing
- `survival_blocks`, `survival_food`, `survival_tools`, `survival_armor`, `survival_potions`
- `survival_mob_drops`, `survival_enchanting`, `survival_farming`, `survival_mining`
- `survival_spawners`, `survival_nether`, `survival_end`

#### Testing
- 137 unit tests across 9 test classes (JUnit 5 + Mockito)
- `PriceCalculatorTest` — price logic, discounts, seasonal modifiers, dynamic pricing
- `StockManagerTest` — stock limit enforcement, bypass permissions
- `TransactionProcessorTest` — buy/sell result codes end-to-end
- `AuctionListingTest` — model logic: bid detection, expiry, min-next-bid calculation
- `SellAllProcessorTest` — best-price selection, sorting, earnings calculation
- `BlackMarketListingTest` — stock tracking, in-stock detection, per-player limits
- `BlackMarketServiceTest` — purchase guard clauses (stock, limits, economy, funds)
- `PriceModifierRepositoryTest` — cache set/get/expire/evict, multi-player/item isolation
- `TradeOfferTest` — confirmation state machine (CONFIRMED_A/B → BOTH_CONFIRMED, reset cycles)

### Fixed (pre-release audit)

- **AuctionHouseGui** — Next-page button was visible when total listing count was an exact multiple of 45 (e.g. 45 listings showing a next-page arrow for a non-existent page 2). Fixed by tracking `totalListingCount` before pagination.
- **BlackMarketGui** — Title and border-material were read from `config.yml` instead of `guis/black_market.yml`. Fixed to use `getGuiConfig("black_market")`.
- **AdminShopGui** — NEXT_SLOT click handler incremented the page counter even when the "no next page" glass pane was displayed. Fixed with an explicit `hasNextPage()` guard.
- **AuctionService** — `completeSale()` and the no-bid branch of `processExpired()` called `player.sendMessage()`, `playSound()`, and `spawnParticle()` from the async expiry scheduler thread (Bukkit API is not thread-safe). Fixed by consolidating all player-facing calls inside `Bukkit.getScheduler().runTask()`.
- **TradeManager** — `expireRequests()` runs on an async timer and was directly calling `plugin.getMessageManager().send()` (which calls `player.sendMessage()`). Fixed by wrapping the notification block in `runTask()`.
- **FluxShopAdminCommand (export)** — Export wrote `mythic-item-id`, `itemsadder-id`, `oraxen-id` but `ShopLoader` reads `mythic-item`, `items-adder`, `oraxen`. Exported files would silently fail to load custom item references on re-import. Keys now match the loader.
- **README / CHANGELOG** — Shop config example used wrong YAML structure (list `- id:` instead of keyed map) and wrong keys (`name:` → `display-name:`, `title:` → `display-name:`, `gui-slot:` → `slot:`). Item reference table corrected to match actual loader keys.

---

*FluxShop v1.0.0 — Initial release*
