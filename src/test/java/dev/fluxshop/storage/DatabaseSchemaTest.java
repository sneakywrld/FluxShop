package dev.fluxshop.storage;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.config.ConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link Database} schema creation and migration.
 *
 * <p>Uses a real SQLite database in a temp directory — no mocking of HikariCP or JDBC.
 * These tests verify that:
 * <ul>
 *   <li>All required tables are created on a fresh database</li>
 *   <li>All required columns are present in each table</li>
 *   <li>Schema creation is idempotent (safe to call twice)</li>
 *   <li>The {@code max_per_player} migration applies correctly</li>
 *   <li>The {@code max_per_player} migration is idempotent</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatabaseSchemaTest {

    @TempDir Path tempDir;

    @Mock FluxShopPlugin plugin;
    @Mock ConfigManager  config;

    private Database db;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("FluxShopTest"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(config.getStorageType()).thenReturn("SQLITE");
        when(config.getString("database.sqlite.file", "fluxshop.db")).thenReturn("test.db");
        when(config.getInt(org.mockito.ArgumentMatchers.anyString(),
                          org.mockito.ArgumentMatchers.anyInt()))
            .thenAnswer(inv -> inv.getArgument(1)); // return default for any int config key

        db = new Database(plugin, config);
    }

    @AfterEach
    void tearDown() {
        if (db != null) db.disconnect();
    }

    // ── Connection ────────────────────────────────────────────────────────────

    @Test
    void connect_freshDatabase_succeeds() {
        assertTrue(db.connect(), "connect() should return true on a fresh SQLite file");
    }

    @Test
    void connect_idempotent_doesNotThrow() {
        // First connect — creates schema
        assertTrue(db.connect());
        db.disconnect();

        // Second connect on same file — all CREATE TABLE IF NOT EXISTS should be no-ops
        // Build a new instance pointing at the same file and connect; tearDown() will close it.
        Database db2 = new Database(plugin, config);
        boolean secondConnect = db2.connect();
        db2.disconnect(); // close before @TempDir cleanup on Windows
        assertTrue(secondConnect,
            "connect() on an existing DB (all tables already present) must return true");
    }

    // ── Table existence ───────────────────────────────────────────────────────

    @Test
    void connect_createsAllRequiredTables() throws Exception {
        db.connect();
        try (Connection conn = db.getConnection()) {
            assertTableExists(conn, "fluxshop_transactions");
            assertTableExists(conn, "fluxshop_stock");
            assertTableExists(conn, "fluxshop_auction_listings");
            assertTableExists(conn, "fluxshop_trade_history");
            assertTableExists(conn, "fluxshop_blackmarket");
            assertTableExists(conn, "fluxshop_bm_purchases");
            assertTableExists(conn, "fluxshop_price_modifiers");
        }
    }

    // ── Column presence ───────────────────────────────────────────────────────

    @Test
    void transactions_hasAllRequiredColumns() throws Exception {
        db.connect();
        try (Connection conn = db.getConnection()) {
            assertColumnExists(conn, "fluxshop_transactions", "uuid");
            assertColumnExists(conn, "fluxshop_transactions", "player");
            assertColumnExists(conn, "fluxshop_transactions", "type");
            assertColumnExists(conn, "fluxshop_transactions", "shop_id");
            assertColumnExists(conn, "fluxshop_transactions", "item_id");
            assertColumnExists(conn, "fluxshop_transactions", "quantity");
            assertColumnExists(conn, "fluxshop_transactions", "price");
            assertColumnExists(conn, "fluxshop_transactions", "currency");
            assertColumnExists(conn, "fluxshop_transactions", "timestamp");
        }
    }

    @Test
    void stock_hasAllRequiredColumns() throws Exception {
        db.connect();
        try (Connection conn = db.getConnection()) {
            assertColumnExists(conn, "fluxshop_stock", "shop_id");
            assertColumnExists(conn, "fluxshop_stock", "item_id");
            assertColumnExists(conn, "fluxshop_stock", "uuid");
            assertColumnExists(conn, "fluxshop_stock", "quantity_sold");
            assertColumnExists(conn, "fluxshop_stock", "reset_at");
        }
    }

    @Test
    void auctionListings_hasAllRequiredColumns() throws Exception {
        db.connect();
        try (Connection conn = db.getConnection()) {
            assertColumnExists(conn, "fluxshop_auction_listings", "seller_uuid");
            assertColumnExists(conn, "fluxshop_auction_listings", "item_data");
            assertColumnExists(conn, "fluxshop_auction_listings", "start_price");
            assertColumnExists(conn, "fluxshop_auction_listings", "buy_now_price");
            assertColumnExists(conn, "fluxshop_auction_listings", "current_bid");
            assertColumnExists(conn, "fluxshop_auction_listings", "bidder_uuid");
            assertColumnExists(conn, "fluxshop_auction_listings", "currency");
            assertColumnExists(conn, "fluxshop_auction_listings", "expires_at");
            assertColumnExists(conn, "fluxshop_auction_listings", "status");
        }
    }

    @Test
    void blackmarket_hasMaxPerPlayerColumn() throws Exception {
        db.connect();
        try (Connection conn = db.getConnection()) {
            assertColumnExists(conn, "fluxshop_blackmarket", "max_per_player");
        }
    }

    // ── Migration idempotency ─────────────────────────────────────────────────

    @Test
    void migration_maxPerPlayer_idempotent() throws Exception {
        // First connect — creates tables and runs the ALTER TABLE migration
        db.connect();
        db.disconnect();

        // Second connect on same file — migration ALTER TABLE runs again on an
        // already-migrated schema. runSilently() must swallow the "column exists" error.
        Database db2 = new Database(plugin, config);
        assertTrue(db2.connect(),
            "Second connect() with migration already applied must not throw");
        try (Connection conn = db2.getConnection()) {
            assertColumnExists(conn, "fluxshop_blackmarket", "max_per_player");
        }
        db2.disconnect(); // close before @TempDir cleanup on Windows
    }

    // ── Async operations ──────────────────────────────────────────────────────

    @Test
    void async_simpleQuery_returnsResult() throws Exception {
        db.connect();
        Long count = db.async(conn -> {
            try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM fluxshop_transactions")) {
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getLong(1) : -1L;
            } catch (Exception e) {
                return -1L;
            }
        }).get();
        assertNotNull(count);
        assertEquals(0L, count, "Fresh table should have 0 rows");
    }

    @Test
    void execute_insertsRow_rowCountIncreases() throws Exception {
        db.connect();
        db.execute(
            "INSERT INTO fluxshop_transactions " +
            "(uuid, player, type, shop_id, item_id, quantity, price, currency, timestamp) " +
            "VALUES (?,?,?,?,?,?,?,?,?)",
            "uuid-1", "TestPlayer", "BUY", "shop1", "item1", 1, 10.0, "vault",
            System.currentTimeMillis()
        );

        // Wait for the async executor to process
        Thread.sleep(200);

        Long count = db.async(conn -> {
            try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM fluxshop_transactions")) {
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getLong(1) : 0L;
            } catch (Exception e) { return 0L; }
        }).get();

        assertNotNull(count);
        assertEquals(1L, count, "One row should have been inserted");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertTableExists(Connection conn, String tableName) throws Exception {
        ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null);
        assertTrue(rs.next(), "Table '" + tableName + "' should exist");
    }

    private void assertColumnExists(Connection conn, String tableName, String columnName) throws Exception {
        ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName);
        assertTrue(rs.next(), "Column '" + columnName + "' should exist in table '" + tableName + "'");
    }
}
