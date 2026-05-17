package dev.fluxshop.storage;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.model.AuctionListing;
import dev.fluxshop.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Async DB operations for auction listings.
 */
public class AuctionRepository {

    private final FluxShopPlugin plugin;
    private final Database       db;

    public AuctionRepository(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.db     = plugin.getDatabase();
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Inserts a new listing and sets its generated id. */
    public CompletableFuture<AuctionListing> insert(AuctionListing listing) {
        return db.async(conn -> {
            String sql = """
                INSERT INTO fluxshop_auction_listings
                (seller_uuid, seller_name, item_data, start_price, buy_now_price, current_bid,
                 bidder_uuid, bidder_name, currency, created_at, expires_at, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1,  listing.getSellerUuid().toString());
                ps.setString(2,  listing.getSellerName());
                ps.setString(3,  ItemSerializer.serialize(listing.getItem()));
                ps.setDouble(4,  listing.getStartPrice());
                ps.setDouble(5,  listing.getBuyNowPrice());
                ps.setDouble(6,  listing.getCurrentBid());
                ps.setString(7,  listing.getCurrentBidder() != null ? listing.getCurrentBidder().toString() : null);
                ps.setString(8,  listing.getCurrentBidderName());
                ps.setString(9,  listing.getCurrency());
                ps.setLong(10,   listing.getCreatedAt().toEpochMilli());
                ps.setLong(11,   listing.getExpiresAt().toEpochMilli());
                ps.setString(12, listing.getStatus().name());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) listing.setId(keys.getLong(1));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("AuctionRepository.insert failed: " + e.getMessage());
            }
            return listing;
        });
    }

    /** Updates current_bid, bidder, expires_at (anti-snipe) and status fields. */
    public void updateBid(AuctionListing listing) {
        db.execute("""
            UPDATE fluxshop_auction_listings
            SET current_bid=?, bidder_uuid=?, bidder_name=?, expires_at=?, status=?
            WHERE id=?
            """,
            listing.getCurrentBid(),
            listing.getCurrentBidder() != null ? listing.getCurrentBidder().toString() : null,
            listing.getCurrentBidderName(),
            listing.getExpiresAt().toEpochMilli(),
            listing.getStatus().name(),
            listing.getId());
    }

    /** Marks the listing as SOLD/EXPIRED/CANCELLED. */
    public void updateStatus(long id, AuctionListing.Status status) {
        db.execute("UPDATE fluxshop_auction_listings SET status=? WHERE id=?",
            status.name(), id);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns all ACTIVE listings, ordered by creation time descending. */
    public CompletableFuture<List<AuctionListing>> getActive() {
        return db.async(conn -> {
            List<AuctionListing> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM fluxshop_auction_listings WHERE status='ACTIVE' ORDER BY created_at DESC")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(fromRow(rs));
            } catch (SQLException e) {
                plugin.getLogger().warning("AuctionRepository.getActive failed: " + e.getMessage());
            }
            return list;
        });
    }

    /** Returns all listings for a specific seller. */
    public CompletableFuture<List<AuctionListing>> getBySeller(UUID sellerUuid) {
        return db.async(conn -> {
            List<AuctionListing> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM fluxshop_auction_listings WHERE seller_uuid=? ORDER BY created_at DESC")) {
                ps.setString(1, sellerUuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(fromRow(rs));
            } catch (SQLException e) {
                plugin.getLogger().warning("AuctionRepository.getBySeller failed: " + e.getMessage());
            }
            return list;
        });
    }

    /** Returns ACTIVE listings where the player has placed the highest bid. */
    public CompletableFuture<List<AuctionListing>> getByBidder(UUID bidderUuid) {
        return db.async(conn -> {
            List<AuctionListing> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM fluxshop_auction_listings WHERE bidder_uuid=? AND status='ACTIVE'")) {
                ps.setString(1, bidderUuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(fromRow(rs));
            } catch (SQLException e) {
                plugin.getLogger().warning("AuctionRepository.getByBidder failed: " + e.getMessage());
            }
            return list;
        });
    }

    /** Returns all ACTIVE listings that have passed their expiry time. */
    public CompletableFuture<List<AuctionListing>> getExpired() {
        long now = System.currentTimeMillis();
        return db.async(conn -> {
            List<AuctionListing> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM fluxshop_auction_listings WHERE status='ACTIVE' AND expires_at<=?")) {
                ps.setLong(1, now);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(fromRow(rs));
            } catch (SQLException e) {
                plugin.getLogger().warning("AuctionRepository.getExpired failed: " + e.getMessage());
            }
            return list;
        });
    }

    public CompletableFuture<AuctionListing> getById(long id) {
        return db.async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM fluxshop_auction_listings WHERE id=?")) {
                ps.setLong(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return fromRow(rs);
            } catch (SQLException e) {
                plugin.getLogger().warning("AuctionRepository.getById failed: " + e.getMessage());
            }
            return null;
        });
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AuctionListing fromRow(ResultSet rs) throws SQLException {
        AuctionListing l = new AuctionListing();
        l.setId(rs.getLong("id"));
        l.setSellerUuid(UUID.fromString(rs.getString("seller_uuid")));
        l.setSellerName(rs.getString("seller_name"));

        String itemData = rs.getString("item_data");
        ItemStack item = ItemSerializer.deserialize(itemData);
        l.setItem(item);

        l.setStartPrice(rs.getDouble("start_price"));
        l.setBuyNowPrice(rs.getDouble("buy_now_price"));
        l.setCurrentBid(rs.getDouble("current_bid"));

        String bidderStr = rs.getString("bidder_uuid");
        if (bidderStr != null) {
            l.setCurrentBidder(UUID.fromString(bidderStr));
            l.setCurrentBidderName(rs.getString("bidder_name"));
        }

        l.setCurrency(rs.getString("currency"));
        l.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
        l.setExpiresAt(Instant.ofEpochMilli(rs.getLong("expires_at")));
        l.setStatus(AuctionListing.Status.valueOf(rs.getString("status")));
        return l;
    }
}
