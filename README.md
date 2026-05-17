# FluxShop

The ultimate Minecraft server shop plugin for **Spigot / Paper 1.8.8 → 1.21.x**.

FluxShop goes beyond a simple buy/sell GUI — it ships with a full **Auction House**, **Player Trade** system, rotating **Black Market**, and an **Admin Analytics Dashboard**, all backed by a robust async SQL engine and a clean developer API.

---

## Features at a Glance

| Category | Features |
|----------|----------|
| **Core Shop** | Multi-shop YAML configs · paginated GUIs · animated icons · per-item buy/sell prices · global & per-player stock limits · on-buy/sell console commands · permission-gated items |
| **Economy** | Flux · Vault · CMI · EssentialsX · PlayerPoints · CoinsEngine · per-shop/per-item currency override · LuckPerms discount groups · sell multiplier groups |
| **Selling** | `/sellall` batch sell · drag-and-drop SellGUI · buy-screen quantity selector (1 / 8 / 16 / 32 / 64 / max-affordable / custom) |
| **Auction House** | Full bidding lifecycle · buy-now · anti-snipe timer · configurable listing fee · per-player listing cap · expiry + collect flow |
| **Player Trades** | GUI-based item + currency swap · dual-confirm anti-scam · atomic rollback on failure · trade history log |
| **Black Market** | Cron-scheduled rotations · configurable item pool · per-player purchase limits · countdown timer · mysterious themed GUI |
| **Analytics** | Top items by volume/revenue · top buyers · economy health score · in-game dashboard · CSV export |
| **Integrations** | Citizens NPC shops · PlaceholderAPI (20+ placeholders) · DiscordSRV transaction logging · WorldGuard region flags · RealisticSeasons price modifiers · GeyserMC/Floodgate Bedrock support |
| **Spawners** | Auto-detects: RoseStacker · WildStacker · SilkSpawners · UltimateStacker · SmartSpawner · EpicSpawners · MineableSpawners · SpawnerMeta · Vanilla |
| **Custom Items** | MythicMobs · ItemsAdder · Oraxen |
| **Performance** | HikariCP connection pool · async DB with `CompletableFuture` · in-memory Caffeine cache · version-safe NMS layer |
| **Content** | 12 pre-configured survival shops (blocks, food, tools, armor, potions, mob drops, enchanting, farming, mining, spawners, nether, end) |

---

## Requirements

- **Server**: Spigot or Paper 1.8.8 – 1.21.x
- **Java**: 17+
- **Economy**: Any supported plugin (see table above)
- Everything else is optional / soft-depend

---

## Installation

1. Download `FluxShop-<version>.jar` and drop it in your `plugins/` folder.
2. Start the server — FluxShop generates default configs.
3. Install an economy plugin (Vault + an economy provider is the safest default).
4. Edit `plugins/FluxShop/config.yml` and the shop files in `plugins/FluxShop/shops/`.
5. Run `/fluxshop reload` to apply changes without a restart.

---

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/shop [section]` | `fluxshop.shop` | Open the server shop (optionally jump to a section) |
| `/sellall [hand\|<material>]` | `fluxshop.sellall` | Sell all sellable items in your inventory |
| `/sellgui` | `fluxshop.sellgui` | Open the drag-and-drop sell interface |
| `/auction [list\|listings\|collect\|history]` | `fluxshop.auction` | Auction House |
| `/trade <player>` | `fluxshop.trade` | Send or accept a trade request |
| `/blackmarket` | `fluxshop.blackmarket` | Open the rotating Black Market |
| `/fluxshop reload` | `fluxshop.admin.reload` | Hot-reload all configs |
| `/fluxshop editor` | `fluxshop.admin.editor` | Open in-game shop editor GUI |
| `/fluxshop give <player> <shopid> <itemid> [amount]` | `fluxshop.admin.give` | Give a shop item to a player |
| `/fluxshop setprice <shopid> <itemid> <buy\|sell> <price>` | `fluxshop.admin.setprice` | Change a price live |
| `/fluxshop setmodifier <player> <itemid> <multiplier> [duration]` | `fluxshop.admin.setmodifier` | Per-player price modifier |
| `/fluxshop restock <shopid> [itemid]` | `fluxshop.admin.restock` | Force-restock a shop |
| `/fluxshop analytics [export]` | `fluxshop.admin.analytics` | View analytics dashboard / export CSV |
| `/fluxshop import essentials` | `fluxshop.admin.import` | Import prices from Essentials worth.yml |
| `/fluxshop export` | `fluxshop.admin.export` | Export shop config to shareable format |
| `/npc fluxshop <npcid> <shopid>` | `fluxshop.admin.editor` | Assign a shop to a Citizens NPC |

---

## Permissions

### Player permissions (default: `true`)
| Permission | Description |
|------------|-------------|
| `fluxshop.shop` | Use `/shop` |
| `fluxshop.sellall` | Use `/sellall` |
| `fluxshop.sellgui` | Use `/sellgui` |
| `fluxshop.auction` | Browse & bid in the Auction House |
| `fluxshop.auction.list` | List items for auction |
| `fluxshop.trade` | Send and receive trade requests |

### Admin permissions (default: `op`)
| Permission | Description |
|------------|-------------|
| `fluxshop.blackmarket` | Access the Black Market |
| `fluxshop.admin` | All admin sub-commands (wildcard) |
| `fluxshop.admin.reload` | `/fluxshop reload` |
| `fluxshop.admin.editor` | In-game GUI editor + Citizens NPC assignment |
| `fluxshop.admin.give` | Give shop items |
| `fluxshop.admin.setprice` | Live price changes |
| `fluxshop.admin.setmodifier` | Per-player price modifiers |
| `fluxshop.admin.restock` | Force restock |
| `fluxshop.admin.analytics` | Analytics dashboard |
| `fluxshop.admin.import` | Import prices |
| `fluxshop.admin.export` | Export config |
| `fluxshop.bypass.stock` | Ignore stock limits |
| `fluxshop.bypass.cooldown` | Ignore trade cooldowns |

### Group permissions
| Permission | Description |
|------------|-------------|
| `fluxshop.discount.<group>` | Buy discount for a named group (e.g. `fluxshop.discount.vip` → 5% off) |
| `fluxshop.multiplier.<group>` | Sell multiplier for a named group (e.g. `fluxshop.multiplier.vip` → ×1.05) |
| `fluxshop.shop.<shopid>` | Access permission for a specific shop |
| `fluxshop.item.<itemid>` | Access permission for a specific item |

Groups and their discount/multiplier values are defined in `config.yml` under `discounts.groups` and `sell-multipliers.groups`.

---

## Configuration

The main config file is `plugins/FluxShop/config.yml`. Every key is documented inline. Key sections:

| Section | Purpose |
|---------|---------|
| `general` | Locale, number abbreviation, banned gamemodes, disabled worlds |
| `database` | SQLite (default) or MySQL with HikariCP pool tuning |
| `economy` | Default provider, per-shop/item overrides, transaction logging |
| `shop` | Purchase cooldown, confirm dialogs, sellall sort order, spawner provider |
| `dynamic-pricing` | Supply/demand price fluctuation settings |
| `discounts` / `sell-multipliers` | Permission-based group discounts and sell bonuses |
| `auction` | Listing fees, duration options, anti-snipe, retention |
| `trade` | Request timeout, cooldown, currency trading toggle |
| `black-market` | Rotation schedule (cron), items per rotation, purchase limits |
| `analytics` | History window, aggregation interval |
| `sounds` / `particles` | Per-event audio and visual effects (all individually configurable) |
| `discord` | DiscordSRV channel routing for shop events |
| `bedrock` | GeyserMC/Floodgate GUI resize and SellGUI disable |
| `realistic-seasons` | Season-based global price modifiers + season-change broadcast |
| `worldguard` | ALLOW_SHOP region flag enforcement |

---

## Shop Configuration

Shops live in `plugins/FluxShop/shops/<shopid>.yml`. A minimal example:

```yaml
id: myshop
display-name: "&6My Shop"
permission: ""          # leave empty for no permission
allowed-worlds: []      # empty = all worlds
blocked-worlds: []

categories:
  gems:
    display-name: "&bGems"
    icon:
      material: DIAMOND
    items:
      diamond:
        material: DIAMOND
        buy-price: 500.0
        sell-price: 200.0
        slot: 0

      emerald:
        material: EMERALD
        buy-price: 300.0
        sell-price: 120.0
        slot: 1
        global-stock: 1000       # server-wide limit
        player-limit: 10         # per-player limit per restock
        buy-permission: "myserver.vip"
```

### Item options reference

| Key | Type | Description |
|-----|------|-------------|
| `material` | String | Bukkit Material name |
| `buy-price` | double | Price to buy (-1 = not buyable) |
| `sell-price` | double | Price when sold (-1 = not sellable) |
| `global-stock` | int | Server-wide max stock (-1 = unlimited) |
| `player-limit` | int | Per-player limit per restock cycle (-1 = unlimited) |
| `auto-restock-seconds` | int | Restock interval in seconds (0 = never) |
| `buy-permission` | String | Permission node required to buy |
| `sell-permission` | String | Permission node required to sell |
| `on-buy-commands` | List | Console commands to run on purchase (`{player}`, `{amount}`) |
| `on-sell-commands` | List | Console commands to run on sell |
| `economy-provider` | String | Override economy for this item (e.g. `playerpoints`) |
| `spawner-type` | String | Entity type for spawner items (e.g. `ZOMBIE`) |
| `mythic-item` | String | MythicMobs item id |
| `items-adder` | String | ItemsAdder `namespace:id` |
| `oraxen` | String | Oraxen item id |
| `dynamic-pricing` | boolean | Enable supply/demand pricing for this item |
| `season-modifiers` | Map | Season → price multiplier (e.g. `SUMMER: 1.2`) |
| `enchantments` | Map | Enchantment name → level |
| `display-name` | String | Override display name in GUI |
| `lore` | List | Custom lore lines |
| `slot` | int | Force a specific GUI slot (0-indexed, -1 = auto) |

---

## PlaceholderAPI

FluxShop registers a `%fluxshop_*%` expansion when PlaceholderAPI is present.

**Economy**

| Placeholder | Returns |
|-------------|---------|
| `%fluxshop_balance%` | Formatted balance (default economy) |
| `%fluxshop_balance_raw%` | Raw double balance |
| `%fluxshop_balance_<provider>%` | Balance for a specific economy provider (e.g. `balance_playerpoints`) |
| `%fluxshop_currency%` | Currency display name |
| `%fluxshop_currency_plural%` | Currency plural name |
| `%fluxshop_currency_symbol%` | Currency symbol |
| `%fluxshop_discount%` | Player's active buy discount % |
| `%fluxshop_sell_multiplier%` | Player's active sell multiplier |

**Shops**

| Placeholder | Returns |
|-------------|---------|
| `%fluxshop_shop_count%` | Number of loaded shops |
| `%fluxshop_shop_<id>_item_count%` | Total items in a specific shop |
| `%fluxshop_shop_<id>_categories%` | Category count in a specific shop |

**Auction House**

| Placeholder | Returns |
|-------------|---------|
| `%fluxshop_auction_count%` | Global active listing count |
| `%fluxshop_player_auction_count%` | Player's own active listing count |
| `%fluxshop_has_collect%` | `true`/`false` — player has items/funds to collect |

**Black Market**

| Placeholder | Returns |
|-------------|---------|
| `%fluxshop_blackmarket_time%` | Time until next rotation (`HH:MM:SS`) |
| `%fluxshop_blackmarket_items%` | Current BM listing count |

**Trade & Analytics**

| Placeholder | Returns |
|-------------|---------|
| `%fluxshop_in_trade%` | `true`/`false` — player is in an active trade |
| `%fluxshop_top_item%` | Most purchased item name (24 h, cached 5 min) |
| `%fluxshop_top_buyer%` | Most active buyer name (24 h, cached 5 min) |
| `%fluxshop_transactions_24h%` | Transaction count in the last 24 hours |
| `%fluxshop_version%` | Plugin version string |

---

## NPC Shops

### Citizens

1. Install [Citizens](https://citizensnpcs.co/).
2. Create an NPC: `/npc create ShopKeeper`
3. Assign the FluxShop trait: `/trait fluxshop`
4. Link a shop: `/npc fluxshop <npc-id> <shop-id>`

### ZNPCsPlus

Add a `FLUXSHOP` interact action to any ZNPCs NPC via the ZNPCsPlus GUI or:

```
/znpcs action add <npcid> INTERACT FLUXSHOP <shopid>
```

In both cases, world restrictions and shop permissions are enforced.

---

## Developer API

The `fluxshop-api` artifact is published to JitPack and contains only the event classes and `FluxShopAPI` facade — no shaded dependencies.

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

Or drop `FluxShop.jar` into a local `libs/` folder and use `compileOnly(files("libs/FluxShop.jar"))`.

See [`DEVELOPER_API.md`](DEVELOPER_API.md) for full examples covering every service and event.

```java
import dev.fluxshop.api.FluxShopAPI;
import dev.fluxshop.api.event.*;

// Access services
ShopManager shops = FluxShopAPI.getShopManager();
AuctionService auction = FluxShopAPI.getAuctionService();
TradeManager trades = FluxShopAPI.getTradeManager();
BlackMarketService bm = FluxShopAPI.getBlackMarketService();
AnalyticsRepository analytics = FluxShopAPI.getAnalytics();

// Listen to events
@EventHandler
public void onPurchase(ShopPurchaseEvent event) {
    Player player = event.getPlayer();
    ShopItem item  = event.getItem();
    double price   = event.getFinalPrice();
    // event.setCancelled(true) to block the purchase
}
```

### Available Events

| Event | Cancellable | Description |
|-------|-------------|-------------|
| `ShopPurchaseEvent` | Yes | Player buying an item |
| `ShopSellEvent` | Yes | Player selling an item |
| `ShopOpenEvent` | Yes | Player opening a shop |
| `AuctionListEvent` | Yes | Item listed for auction |
| `AuctionBidEvent` | Yes | Bid placed on a listing |
| `AuctionSoldEvent` | No | Auction listing sold |
| `TradeCompleteEvent` | No | Trade completed |
| `BlackMarketPurchaseEvent` | Yes | Black Market item purchased |

---

## Default Shops

FluxShop ships 12 pre-configured survival economy shops:

| Shop ID | Contents |
|---------|---------|
| `survival_blocks` | Stone, wood, glass, concrete, wool, ores, terracotta, and more |
| `survival_food` | Crops, cooked meats, raw foods, golden apples |
| `survival_tools` | All tool tiers (wood → netherite) + misc tools |
| `survival_armor` | All armor tiers (leather → netherite), elytra, shields |
| `survival_potions` | Regular, splash, lingering potions + brewing ingredients |
| `survival_mob_drops` | Common, nether, end, rare drops + wither skulls |
| `survival_enchanting` | Books, materials, top enchantment books |
| `survival_farming` | Seeds, saplings, bonemeal, flowers, dyes |
| `survival_mining` | Explosives, rails, redstone components, utility blocks |
| `survival_spawners` | Common → premium spawners (Zombie to Elder Guardian) |
| `survival_nether` | Nether blocks, resources, ancient debris |
| `survival_end` | End Stone, purpur, chorus, shulker boxes, elytra |

Prices follow standard survival economy ratios; sell prices are ~40–60% of buy prices.

---

## Building from Source

```bash
git clone https://github.com/sneakywrld/FluxShop
cd FluxShop
./gradlew shadowJar
# Output: build/libs/FluxShop-<version>.jar
```

Requirements: **JDK 21+**, **Gradle 8.7** (wrapper included).

The Flux API dependency (`../Flux/flux-api/build/libs/flux-api-1.0.0.jar`) is optional — remove the `compileOnly(files(...))` line in `build.gradle.kts` if you don't have it locally.

---

## Support

- **Issues**: Open a GitHub issue with your server version, FluxShop version, and full console output.
- **Config help**: Every key in `config.yml` is commented — read there first.
- **Reload**: Always try `/fluxshop reload` after config changes before restarting.

---

## License

All rights reserved. For server use only. Redistribution or resale is not permitted.
