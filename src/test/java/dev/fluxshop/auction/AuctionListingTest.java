package dev.fluxshop.auction;

import dev.fluxshop.model.AuctionListing;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AuctionListing} — pure model logic, no Bukkit deps.
 */
class AuctionListingTest {

    // ── hasBid ────────────────────────────────────────────────────────────────

    @Test
    void hasBid_noBidderSet_returnsFalse() {
        AuctionListing listing = new AuctionListing();
        assertFalse(listing.hasBid());
    }

    @Test
    void hasBid_bidderSet_returnsTrue() {
        AuctionListing listing = new AuctionListing();
        listing.setCurrentBidder(UUID.randomUUID());
        assertTrue(listing.hasBid());
    }

    // ── hasBuyNow ─────────────────────────────────────────────────────────────

    @Test
    void hasBuyNow_negativeOne_returnsFalse() {
        AuctionListing listing = new AuctionListing();
        listing.setBuyNowPrice(-1);
        assertFalse(listing.hasBuyNow());
    }

    @Test
    void hasBuyNow_zero_returnsFalse() {
        AuctionListing listing = new AuctionListing();
        listing.setBuyNowPrice(0);
        assertFalse(listing.hasBuyNow());
    }

    @Test
    void hasBuyNow_positivePrice_returnsTrue() {
        AuctionListing listing = new AuctionListing();
        listing.setBuyNowPrice(500.0);
        assertTrue(listing.hasBuyNow());
    }

    // ── isActive ──────────────────────────────────────────────────────────────

    @Test
    void isActive_freshListing_returnsTrue() {
        AuctionListing listing = new AuctionListing();
        listing.setStatus(AuctionListing.Status.ACTIVE);
        assertTrue(listing.isActive());
    }

    @Test
    void isActive_soldListing_returnsFalse() {
        AuctionListing listing = new AuctionListing();
        listing.setStatus(AuctionListing.Status.SOLD);
        assertFalse(listing.isActive());
    }

    @Test
    void isActive_expiredListing_returnsFalse() {
        AuctionListing listing = new AuctionListing();
        listing.setStatus(AuctionListing.Status.EXPIRED);
        assertFalse(listing.isActive());
    }

    // ── isExpired ─────────────────────────────────────────────────────────────

    @Test
    void isExpired_activeAndPastExpiry_returnsTrue() {
        AuctionListing listing = new AuctionListing();
        listing.setStatus(AuctionListing.Status.ACTIVE);
        listing.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        assertTrue(listing.isExpired());
    }

    @Test
    void isExpired_activeAndFutureExpiry_returnsFalse() {
        AuctionListing listing = new AuctionListing();
        listing.setStatus(AuctionListing.Status.ACTIVE);
        listing.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        assertFalse(listing.isExpired());
    }

    @Test
    void isExpired_soldAndPastExpiry_returnsFalse() {
        // A SOLD listing is never "expired" even if the time has passed
        AuctionListing listing = new AuctionListing();
        listing.setStatus(AuctionListing.Status.SOLD);
        listing.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        assertFalse(listing.isExpired());
    }

    // ── getMinNextBid ─────────────────────────────────────────────────────────

    @Test
    void getMinNextBid_noBidYet_returnsStartPrice() {
        AuctionListing listing = new AuctionListing();
        listing.setStartPrice(100.0);
        // No bidder set → hasBid() = false

        assertEquals(100.0, listing.getMinNextBid(5.0));
    }

    @Test
    void getMinNextBid_withExistingBid_returnsBidPlusIncrement() {
        AuctionListing listing = new AuctionListing();
        listing.setStartPrice(100.0);
        listing.setCurrentBid(200.0);
        listing.setCurrentBidder(UUID.randomUUID());

        // 200 × (1 + 5/100) = 200 × 1.05 = 210.0
        assertEquals(210.0, listing.getMinNextBid(5.0), 0.001);
    }

    @Test
    void getMinNextBid_zeroIncrement_returnsSameBid() {
        AuctionListing listing = new AuctionListing();
        listing.setCurrentBid(500.0);
        listing.setCurrentBidder(UUID.randomUUID());

        assertEquals(500.0, listing.getMinNextBid(0.0), 0.001);
    }

    @Test
    void getMinNextBid_tenPercentIncrement() {
        AuctionListing listing = new AuctionListing();
        listing.setCurrentBid(1000.0);
        listing.setCurrentBidder(UUID.randomUUID());

        // 1000 × 1.10 = 1100.0
        assertEquals(1100.0, listing.getMinNextBid(10.0), 0.001);
    }

    @Test
    void getMinNextBid_incrementCompoundsOnHighBids() {
        AuctionListing listing = new AuctionListing();
        listing.setCurrentBid(999.99);
        listing.setCurrentBidder(UUID.randomUUID());

        double expected = 999.99 * 1.05;
        assertEquals(expected, listing.getMinNextBid(5.0), 0.001);
    }

    // ── Status transitions ────────────────────────────────────────────────────

    @Test
    void statusEnum_hasExpectedValues() {
        // Ensure all expected statuses exist (regression guard)
        assertNotNull(AuctionListing.Status.valueOf("ACTIVE"));
        assertNotNull(AuctionListing.Status.valueOf("SOLD"));
        assertNotNull(AuctionListing.Status.valueOf("EXPIRED"));
        assertNotNull(AuctionListing.Status.valueOf("CANCELLED"));
    }

    // ── Field defaults ────────────────────────────────────────────────────────

    @Test
    void newListing_defaultStatus_isActive() {
        AuctionListing listing = new AuctionListing();
        assertEquals(AuctionListing.Status.ACTIVE, listing.getStatus());
        assertTrue(listing.isActive());
    }

    @Test
    void newListing_noBidder_hasBidFalse() {
        AuctionListing listing = new AuctionListing();
        assertFalse(listing.hasBid());
        assertNull(listing.getCurrentBidder());
    }
}
