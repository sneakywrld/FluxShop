package dev.fluxshop;

import dev.fluxshop.api.FluxShopAPI;
import dev.fluxshop.auction.AuctionService;
import dev.fluxshop.blackmarket.BlackMarketService;
import dev.fluxshop.compat.DiscordLogger;
import dev.fluxshop.compat.CompatManager;
import dev.fluxshop.config.ConfigManager;
import dev.fluxshop.config.MessageManager;
import dev.fluxshop.economy.EconomyRegistry;
import dev.fluxshop.gui.GuiManager;
import dev.fluxshop.listener.GuiListener;
import dev.fluxshop.listener.PlayerListener;
import dev.fluxshop.listener.ShopStandListener;
import dev.fluxshop.nms.NMSHandler;
import dev.fluxshop.nms.VersionUtil;
import dev.fluxshop.shop.ShopManager;
import dev.fluxshop.shop.ShopStandManager;
import dev.fluxshop.storage.AnalyticsRepository;
import dev.fluxshop.storage.Database;
import dev.fluxshop.storage.PriceModifierRepository;
import dev.fluxshop.trade.TradeManager;
import dev.fluxshop.util.ParticleManager;
import dev.fluxshop.util.SoundManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class FluxShopPlugin extends JavaPlugin {

    private static FluxShopPlugin instance;

    private static final int BSTATS_PLUGIN_ID = 31387;

    // ── Core Services ────────────────────────────────────────────────────────
    private ConfigManager                configManager;
    private MessageManager               messageManager;
    private NMSHandler                   nmsHandler;
    private Database                     database;
    private EconomyRegistry              economyRegistry;
    private CompatManager                compatManager;
    private ShopManager                  shopManager;
    private ShopStandManager             shopStandManager;
    private GuiManager                   guiManager;
    private SoundManager                 soundManager;
    private ParticleManager              particleManager;
    private dev.fluxshop.shop.PriceCalculator priceCalculator;
    private AuctionService               auctionService;
    private TradeManager                 tradeManager;
    private BlackMarketService           blackMarketService;
    private AnalyticsRepository          analyticsRepository;
    private PriceModifierRepository      priceModifierRepository;
    private DiscordLogger                discordLogger;

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║         FluxShop v" + getDescription().getVersion() + "              ║");
        getLogger().info("║     The Ultimate Server Shop         ║");
        getLogger().info("╚══════════════════════════════════════╝");

        // 1. Detect server version & load NMS handler
        nmsHandler = VersionUtil.loadHandler(this);
        if (nmsHandler == null) {
            getLogger().severe("Unsupported server version! FluxShop will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("NMS handler loaded for " + VersionUtil.getVersionString());

        // 2. Configuration & messages
        // Save default resource files if they don't exist in the data folder
        saveDefaultResources();
        configManager  = new ConfigManager(this);
        configManager.load();
        messageManager = new MessageManager(this, configManager);
        messageManager.load();

        // 3. Database
        database = new Database(this, configManager);
        if (!database.connect()) {
            getLogger().severe("Failed to connect to database! FluxShop will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Economy & compatibility hooks
        economyRegistry = new EconomyRegistry(this);
        compatManager   = new CompatManager(this);
        compatManager.registerAll();

        // 5. Particle & sound systems
        soundManager    = new SoundManager(this, configManager);
        particleManager = new ParticleManager(this, configManager);

        // 6. GUI system
        guiManager = new GuiManager(this);

        // 7. Shop engine (loads all shops from disk) and stand registry
        shopManager      = new ShopManager(this);
        shopStandManager = new ShopStandManager(this);
        priceCalculator  = new dev.fluxshop.shop.PriceCalculator(this);
        shopManager.loadAll();
        shopStandManager.load();

        // 8. Feature services (Auction, Trade, BlackMarket, Analytics, Discord)
        discordLogger       = new DiscordLogger(this);
        auctionService      = new AuctionService(this);
        auctionService.start();
        tradeManager        = new TradeManager(this);
        tradeManager.start();
        blackMarketService      = new BlackMarketService(this);
        blackMarketService.start();
        analyticsRepository     = new AnalyticsRepository(this);
        priceModifierRepository = new PriceModifierRepository(this);

        // Purge expired price modifiers every hour
        getServer().getScheduler().runTaskTimerAsynchronously(this,
            () -> priceModifierRepository.purgeExpired(), 20L * 3600, 20L * 3600);

        // 9. Commands
        registerCommands();

        // 9. Listeners
        registerListeners();

        // 10. Public API
        FluxShopAPI.init(this);

        // 11. bStats metrics (opt-out via config: general.metrics: false)
        if (configManager.getBoolean("general.metrics", true)) {
            new Metrics(this, BSTATS_PLUGIN_ID);
        }

        long elapsed = System.currentTimeMillis() - start;
        getLogger().info("FluxShop enabled in " + elapsed + "ms — " + shopManager.getShopCount() + " shops loaded.");
    }

    @Override
    public void onDisable() {
        if (auctionService    != null) auctionService.stop();
        if (tradeManager      != null) tradeManager.stop();
        if (blackMarketService!= null) blackMarketService.stop();
        // Cleanly close all open GUIs before shutdown
        if (guiManager        != null) guiManager.closeAll();
        if (shopManager       != null) shopManager.saveAll();
        if (shopStandManager  != null) shopStandManager.save();
        if (database != null) database.disconnect();
        FluxShopAPI.shutdown();

        getLogger().info("FluxShop disabled. Goodbye!");
    }

    /**
     * Hot-reloads all configs, messages, and shop data without restarting the plugin.
     */
    public void reload() {
        try {
            guiManager.closeAll();
            configManager.load();
            messageManager.load();
            soundManager.reload();
            particleManager.reload();
            shopManager.loadAll();
            shopStandManager.load();
            getLogger().info("FluxShop reloaded successfully.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during reload", e);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public static FluxShopPlugin getInstance() { return instance; }

    public ConfigManager   getConfigManager()   { return configManager;   }
    public MessageManager  getMessageManager()  { return messageManager;  }
    public NMSHandler      getNMSHandler()      { return nmsHandler;      }
    public Database        getDatabase()        { return database;        }
    public EconomyRegistry getEconomyRegistry() { return economyRegistry; }
    public CompatManager   getCompatManager()   { return compatManager;   }
    public ShopManager       getShopManager()       { return shopManager;       }
    public ShopStandManager  getShopStandManager()  { return shopStandManager;  }
    public GuiManager      getGuiManager()      { return guiManager;      }
    public SoundManager    getSoundManager()     { return soundManager;     }
    public ParticleManager getParticleManager()  { return particleManager;  }
    public dev.fluxshop.shop.PriceCalculator getPriceCalculator() { return priceCalculator; }
    public AuctionService      getAuctionService()     { return auctionService;      }
    public TradeManager        getTradeManager()       { return tradeManager;        }
    public BlackMarketService  getBlackMarketService() { return blackMarketService;  }
    public AnalyticsRepository      getAnalyticsRepository()     { return analyticsRepository;     }
    public PriceModifierRepository  getPriceModifierRepository() { return priceModifierRepository; }
    public DiscordLogger            getDiscordLogger()           { return discordLogger;            }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private void registerCommands() {
        // Commands are registered through CommandManager which reads from plugin.yml
        // Each executor is wired in CommandManager
        new dev.fluxshop.command.CommandManager(this).registerAll();
    }

    private void saveDefaultResources() {
        // These are additional resource files beyond config.yml/messages.yml that ConfigManager
        // doesn't copy. saveResource() only copies if the target file does not already exist.
        String[] extras = { "black_market_pool.yml" };
        for (String name : extras) {
            try {
                saveResource(name, false);
            } catch (IllegalArgumentException ignored) {
                // Not present in JAR — skip
            }
        }
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new GuiListener(this),       this);
        pm.registerEvents(new PlayerListener(this),    this);
        pm.registerEvents(new ShopStandListener(this), this);
    }
}
