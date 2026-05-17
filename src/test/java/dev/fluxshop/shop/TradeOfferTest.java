package dev.fluxshop.shop;

import dev.fluxshop.model.TradeOffer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TradeOffer} confirmation state-machine logic.
 *
 * <p>No Bukkit dependencies — pure model tests.
 */
class TradeOfferTest {

    private TradeOffer trade() {
        TradeOffer offer = new TradeOffer();
        offer.setPlayerA(UUID.randomUUID());
        offer.setPlayerB(UUID.randomUUID());
        offer.setStatus(TradeOffer.Status.ACTIVE);
        return offer;
    }

    // ── Default state ─────────────────────────────────────────────────────────

    @Test
    void newOffer_defaultStatus_isPending() {
        TradeOffer offer = new TradeOffer();
        assertEquals(TradeOffer.Status.PENDING, offer.getStatus());
    }

    @Test
    void newOffer_currencyAmounts_defaultToZero() {
        TradeOffer offer = new TradeOffer();
        assertEquals(0.0, offer.getCurrencyAmountA(), 0.0001);
        assertEquals(0.0, offer.getCurrencyAmountB(), 0.0001);
    }

    @Test
    void newOffer_itemLists_emptyByDefault() {
        TradeOffer offer = new TradeOffer();
        assertNotNull(offer.getItemsA());
        assertNotNull(offer.getItemsB());
        assertTrue(offer.getItemsA().isEmpty());
        assertTrue(offer.getItemsB().isEmpty());
    }

    @Test
    void statusEnum_hasAllExpectedValues() {
        assertNotNull(TradeOffer.Status.valueOf("PENDING"));
        assertNotNull(TradeOffer.Status.valueOf("ACTIVE"));
        assertNotNull(TradeOffer.Status.valueOf("CONFIRMED_A"));
        assertNotNull(TradeOffer.Status.valueOf("CONFIRMED_B"));
        assertNotNull(TradeOffer.Status.valueOf("BOTH_CONFIRMED"));
        assertNotNull(TradeOffer.Status.valueOf("COMPLETE"));
        assertNotNull(TradeOffer.Status.valueOf("CANCELLED"));
    }

    // ── Single confirmations ──────────────────────────────────────────────────

    @Test
    void confirmA_whenBNotConfirmed_becomesConfirmedA() {
        TradeOffer offer = trade();
        offer.setConfirmedA(true);
        assertEquals(TradeOffer.Status.CONFIRMED_A, offer.getStatus());
    }

    @Test
    void confirmB_whenANotConfirmed_becomesConfirmedB() {
        TradeOffer offer = trade();
        offer.setConfirmedB(true);
        assertEquals(TradeOffer.Status.CONFIRMED_B, offer.getStatus());
    }

    // ── Both confirmations ────────────────────────────────────────────────────

    @Test
    void confirmA_whenBAlreadyConfirmed_becomesBothConfirmed() {
        TradeOffer offer = trade();
        offer.setConfirmedB(true); // B first
        offer.setConfirmedA(true); // A second → should trigger BOTH_CONFIRMED
        assertEquals(TradeOffer.Status.BOTH_CONFIRMED, offer.getStatus());
    }

    @Test
    void confirmB_whenAAlreadyConfirmed_becomesBothConfirmed() {
        TradeOffer offer = trade();
        offer.setConfirmedA(true); // A first
        offer.setConfirmedB(true); // B second → should trigger BOTH_CONFIRMED
        assertEquals(TradeOffer.Status.BOTH_CONFIRMED, offer.getStatus());
    }

    // ── isConfirmedByA / isConfirmedByB ───────────────────────────────────────

    @Test
    void isConfirmedByA_confirmedA_returnsTrue() {
        TradeOffer offer = trade();
        offer.setStatus(TradeOffer.Status.CONFIRMED_A);
        assertTrue(offer.isConfirmedByA());
    }

    @Test
    void isConfirmedByA_confirmedB_returnsFalse() {
        TradeOffer offer = trade();
        offer.setStatus(TradeOffer.Status.CONFIRMED_B);
        assertFalse(offer.isConfirmedByA());
    }

    @Test
    void isConfirmedByA_bothConfirmed_returnsTrue() {
        TradeOffer offer = trade();
        offer.setStatus(TradeOffer.Status.BOTH_CONFIRMED);
        assertTrue(offer.isConfirmedByA());
    }

    @Test
    void isConfirmedByB_confirmedB_returnsTrue() {
        TradeOffer offer = trade();
        offer.setStatus(TradeOffer.Status.CONFIRMED_B);
        assertTrue(offer.isConfirmedByB());
    }

    @Test
    void isConfirmedByB_confirmedA_returnsFalse() {
        TradeOffer offer = trade();
        offer.setStatus(TradeOffer.Status.CONFIRMED_A);
        assertFalse(offer.isConfirmedByB());
    }

    @Test
    void isConfirmedByB_bothConfirmed_returnsTrue() {
        TradeOffer offer = trade();
        offer.setStatus(TradeOffer.Status.BOTH_CONFIRMED);
        assertTrue(offer.isConfirmedByB());
    }

    // ── resetConfirmations ────────────────────────────────────────────────────

    @Test
    void resetConfirmations_fromConfirmedA_becomesActive() {
        TradeOffer offer = trade();
        offer.setConfirmedA(true);
        assertEquals(TradeOffer.Status.CONFIRMED_A, offer.getStatus());
        offer.resetConfirmations();
        assertEquals(TradeOffer.Status.ACTIVE, offer.getStatus());
    }

    @Test
    void resetConfirmations_fromConfirmedB_becomesActive() {
        TradeOffer offer = trade();
        offer.setConfirmedB(true);
        offer.resetConfirmations();
        assertEquals(TradeOffer.Status.ACTIVE, offer.getStatus());
    }

    @Test
    void resetConfirmations_fromBothConfirmed_becomesActive() {
        TradeOffer offer = trade();
        offer.setConfirmedA(true);
        offer.setConfirmedB(true);
        assertEquals(TradeOffer.Status.BOTH_CONFIRMED, offer.getStatus());
        offer.resetConfirmations();
        assertEquals(TradeOffer.Status.ACTIVE, offer.getStatus());
    }

    @Test
    void resetConfirmations_fromActive_remainsActive() {
        TradeOffer offer = trade();
        // Status is already ACTIVE; reset should be a no-op
        offer.resetConfirmations();
        assertEquals(TradeOffer.Status.ACTIVE, offer.getStatus());
    }

    @Test
    void resetConfirmations_fromComplete_noChange() {
        TradeOffer offer = trade();
        offer.setStatus(TradeOffer.Status.COMPLETE);
        offer.resetConfirmations();
        // COMPLETE is not a confirmation state — reset should not change it
        assertEquals(TradeOffer.Status.COMPLETE, offer.getStatus());
    }

    @Test
    void resetConfirmations_fromCancelled_noChange() {
        TradeOffer offer = trade();
        offer.setStatus(TradeOffer.Status.CANCELLED);
        offer.resetConfirmations();
        assertEquals(TradeOffer.Status.CANCELLED, offer.getStatus());
    }

    // ── setConfirmedX(false) resets ───────────────────────────────────────────

    @Test
    void setConfirmedA_false_fromConfirmedA_becomesActive() {
        TradeOffer offer = trade();
        offer.setConfirmedA(true);
        offer.setConfirmedA(false);
        assertEquals(TradeOffer.Status.ACTIVE, offer.getStatus());
    }

    @Test
    void setConfirmedB_false_fromBothConfirmed_becomesActive() {
        TradeOffer offer = trade();
        offer.setConfirmedA(true);
        offer.setConfirmedB(true);
        offer.setConfirmedB(false);
        assertEquals(TradeOffer.Status.ACTIVE, offer.getStatus());
    }

    // ── Re-confirm after reset ────────────────────────────────────────────────

    @Test
    void reconfirmAfterReset_backToBothConfirmed() {
        TradeOffer offer = trade();
        // Confirm both
        offer.setConfirmedA(true);
        offer.setConfirmedB(true);
        assertEquals(TradeOffer.Status.BOTH_CONFIRMED, offer.getStatus());
        // Reset (e.g. player changed their offer)
        offer.resetConfirmations();
        assertEquals(TradeOffer.Status.ACTIVE, offer.getStatus());
        // Re-confirm both
        offer.setConfirmedA(true);
        offer.setConfirmedB(true);
        assertEquals(TradeOffer.Status.BOTH_CONFIRMED, offer.getStatus());
    }

    // ── isConfirmedByX transitions agree with setConfirmedX ──────────────────

    @Test
    void isConfirmedByA_agreesWithSetConfirmedA_throughFullCycle() {
        TradeOffer offer = trade();
        assertFalse(offer.isConfirmedByA());
        offer.setConfirmedA(true);
        assertTrue(offer.isConfirmedByA());
        offer.setConfirmedA(false);
        assertFalse(offer.isConfirmedByA());
    }

    @Test
    void isConfirmedByB_agreesWithSetConfirmedB_throughFullCycle() {
        TradeOffer offer = trade();
        assertFalse(offer.isConfirmedByB());
        offer.setConfirmedB(true);
        assertTrue(offer.isConfirmedByB());
        offer.setConfirmedB(false);
        assertFalse(offer.isConfirmedByB());
    }
}
