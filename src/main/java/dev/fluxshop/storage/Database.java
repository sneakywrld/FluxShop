package dev.fluxshop.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.config.ConfigManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Manages the database connection pool and schema.
 *
 * <p>Supports SQLite (file-based, no setup required) and MySQL/MariaDB (via HikariCP).
 * All queries should be executed via {@link #async(Function)} to avoid blocking the main thread.
 */
public class Database {

    private final FluxShopPlugin  plugin;
    private final ConfigManager   config;
    private HikariDataSource      dataSource;
    // Single-threaded executor ensures all DB operations are serialised in submission order.
    // This prevents races where fire-and-forget execute() calls (e.g. clearAll) race against
    // subsequent async() inserts, which is a real risk with MySQL's multi-connection pool.
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FluxShop-DB");
        t.setDaemon(true);
        return t;
    });

    public Database(FluxShopPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Opens the connection pool and creates/migrates all tables.
     *
     * @return {@code true} if the connection was established successfully
     */
    public boolean connect() {
        try {
            HikariConfig hikari = buildHikariConfig();
            dataSource = new HikariDataSource(hikari);
            createTables();
            plugin.getLogger().info("Database connected (" + config.getStorageType() + ").");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to database", e);
            return false;
        }
    }

    public void disconnect() {
        executor.shutdown();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ── Connection ────────────────────────────────────────────────────────────

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Executes a database operation asynchronously.
     *
     * @param function receives a {@link Connection} and returns a result
     */
    public <T> CompletableFuture<T> async(Function<Connection, T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                return function.apply(conn);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database error", e);
                return null;
            } catch (Exception e) {
                // Catches RuntimeExceptions thrown from inside the lambda (e.g. wrapped SQLExceptions)
                plugin.getLogger().log(Level.SEVERE, "Unexpected database error", e);
                return null;
            }
        }, executor);
    }

    /**
     * Executes a fire-and-forget database update asynchronously.
     */
    public void execute(String sql, Object... params) {
        async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "SQL error: " + sql, e);
            }
            return null;
        });
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    private void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            // Transactions log
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS fluxshop_transactions (
                    id         INTEGER PRIMARY KEY %s,
                    uuid       VARCHAR(36)  NOT NULL,
                    player     VARCHAR(16)  NOT NULL,
                    type       VARCHAR(4)   NOT NULL,
                    shop_id    VARCHAR(64)  NOT NULL,
                    category   VARCHAR(64),
                    item_id    VARCHAR(128) NOT NULL,
                    item_name  VARCHAR(256),
                    quantity   INTEGER      NOT NULL,
                    price      DOUBLE       NOT NULL,
                    currency   VARCHAR(32)  NOT NULL,
                    timestamp  BIGINT       NOT NULL
                )
                """.formatted(autoIncrement()));

            // Stock tracking (global + per-player)
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS fluxshop_stock (
                    id           INTEGER PRIMARY KEY %s,
                    shop_id      VARCHAR(64) NOT NULL,
                    item_id      VARCHAR(128) NOT NULL,
                    uuid         VARCHAR(36),
                    quantity_sold INTEGER NOT NULL DEFAULT 0,
                    reset_at      BIGINT NOT NULL DEFAULT 0,
                    UNIQUE(shop_id, item_id, uuid)
                )
                """.formatted(autoIncrement()));

            // Auction listings
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS fluxshop_auction_listings (
                    id               INTEGER PRIMARY KEY %s,
                    seller_uuid      VARCHAR(36)   NOT NULL,
                    seller_name      VARCHAR(16)   NOT NULL,
                    item_data        TEXT          NOT NULL,
                    start_price      DOUBLE        NOT NULL,
                    buy_now_price    DOUBLE        NOT NULL DEFAULT -1,
                    current_bid      DOUBLE        NOT NULL DEFAULT 0,
                    bidder_uuid      VARCHAR(36),
                    bidder_name      VARCHAR(16),
                    currency         VARCHAR(32)   NOT NULL,
                    created_at       BIGINT        NOT NULL,
                    expires_at       BIGINT        NOT NULL,
                    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE'
                )
                """.formatted(autoIncrement()));

            // Trade history
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS fluxshop_trade_history (
                    id           INTEGER PRIMARY KEY %s,
                    player_a     VARCHAR(36) NOT NULL,
                    player_b     VARCHAR(36) NOT NULL,
                    items_a      TEXT,
                    items_b      TEXT,
                    currency_a   DOUBLE NOT NULL DEFAULT 0,
                    currency_b   DOUBLE NOT NULL DEFAULT 0,
                    currency     VARCHAR(32),
                    completed_at BIGINT NOT NULL
                )
                """.formatted(autoIncrement()));

            // Black market state
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS fluxshop_blackmarket (
                    id           INTEGER PRIMARY KEY %s,
                    item_data    TEXT    NOT NULL,
                    price        DOUBLE  NOT NULL,
                    currency     VARCHAR(32) NOT NULL,
                    stock        INTEGER NOT NULL,
                    purchased    INTEGER NOT NULL DEFAULT 0,
                    next_refresh BIGINT  NOT NULL
                )
                """.formatted(autoIncrement()));

            // Per-player black market purchase tracking
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS fluxshop_bm_purchases (
                    listing_id INTEGER NOT NULL,
                    uuid       VARCHAR(36) NOT NULL,
                    quantity   INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (listing_id, uuid)
                )
                """);

            // Per-player price modifiers
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS fluxshop_price_modifiers (
                    uuid       VARCHAR(36)  NOT NULL,
                    item_id    VARCHAR(128) NOT NULL,
                    modifier   DOUBLE       NOT NULL,
                    expires_at BIGINT       NOT NULL,
                    PRIMARY KEY (uuid, item_id)
                )
                """);

            // Migration: add max_per_player to black market table if missing (silently ignored if already exists)
            runSilently(conn, "ALTER TABLE fluxshop_blackmarket ADD COLUMN max_per_player INTEGER NOT NULL DEFAULT 3");

            // Create indexes for frequent query patterns
            runSilently(conn, "CREATE INDEX IF NOT EXISTS idx_tx_uuid   ON fluxshop_transactions(uuid)");
            runSilently(conn, "CREATE INDEX IF NOT EXISTS idx_tx_time   ON fluxshop_transactions(timestamp)");
            runSilently(conn, "CREATE INDEX IF NOT EXISTS idx_stock_key ON fluxshop_stock(shop_id, item_id, uuid)");
            runSilently(conn, "CREATE INDEX IF NOT EXISTS idx_ah_status ON fluxshop_auction_listings(status)");
            runSilently(conn, "CREATE INDEX IF NOT EXISTS idx_ah_seller ON fluxshop_auction_listings(seller_uuid)");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private HikariConfig buildHikariConfig() {
        HikariConfig hikari = new HikariConfig();

        if (config.getStorageType().equals("MYSQL")) {
            String host  = config.getString("database.mysql.host",     "localhost");
            int    port  = config.getInt   ("database.mysql.port",     3306);
            String db    = config.getString("database.mysql.database", "fluxshop");
            String user  = config.getString("database.mysql.username", "root");
            String pass  = config.getString("database.mysql.password", "");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=false&autoReconnect=true&characterEncoding=utf8");
            hikari.setUsername(user);
            hikari.setPassword(pass);
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setMaximumPoolSize(config.getInt("database.mysql.pool.maximum-pool-size", 10));
            hikari.setMinimumIdle(config.getInt("database.mysql.pool.minimum-idle", 2));
            hikari.setConnectionTimeout(config.getInt("database.mysql.pool.connection-timeout", 30000));
            hikari.setIdleTimeout(config.getInt("database.mysql.pool.idle-timeout", 600000));
            hikari.setMaxLifetime(config.getInt("database.mysql.pool.max-lifetime", 1800000));
        } else {
            // SQLite
            File dbFile = new File(plugin.getDataFolder(),
                config.getString("database.sqlite.file", "fluxshop.db"));
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1); // SQLite is single-threaded
            hikari.addDataSourceProperty("busy_timeout", "30000");
        }

        hikari.setPoolName("FluxShop-DB");
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return hikari;
    }

    private String autoIncrement() {
        return config.getStorageType().equals("MYSQL") ? "AUTO_INCREMENT" : "AUTOINCREMENT";
    }

    private void runSilently(Connection conn, String sql) {
        try {
            conn.createStatement().execute(sql);
        } catch (SQLException ignored) {}
    }
}
