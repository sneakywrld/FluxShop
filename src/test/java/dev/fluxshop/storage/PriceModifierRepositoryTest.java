package dev.fluxshop.storage;

import dev.fluxshop.FluxShopPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PriceModifierRepository} in-memory cache behaviour.
 *
 * <p>The Database is mocked so these tests run without a real SQLite file.
 * All assertions exercise the cache layer only.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceModifierRepositoryTest {

    @Mock FluxShopPlugin plugin;
    @Mock Database        db;

    PriceModifierRepository repo;

    private static final UUID PLAYER  = UUID.randomUUID();
    private static final UUID PLAYER2 = UUID.randomUUID();
    private static final String ITEM   = "cobblestone";
    private static final String ITEM2  = "diamond";

    @BeforeEach
    void setUp() {
        when(plugin.getDatabase()).thenReturn(db);
        // db.execute() is fire-and-forget; make it a no-op
        when(db.async(any())).thenAnswer(inv -> java.util.concurrent.CompletableFuture.completedFuture(null));
        repo = new PriceModifierRepository(plugin);
    }

    // ── getModifier — no entry ────────────────────────────────────────────────

    @Test
    void getModifier_noEntry_returnsOne() {
        assertEquals(1.0, repo.getModifier(PLAYER, ITEM), 0.0001);
    }

    @Test
    void getModifier_unknownPlayer_returnsOne() {
        repo.setModifier(PLAYER, ITEM, 0.9, 0);
        assertEquals(1.0, repo.getModifier(PLAYER2, ITEM), 0.0001);
    }

    @Test
    void getModifier_unknownItem_returnsOne() {
        repo.setModifier(PLAYER, ITEM, 0.9, 0);
        assertEquals(1.0, repo.getModifier(PLAYER, ITEM2), 0.0001);
    }

    // ── setModifier / getModifier — happy path ────────────────────────────────

    @Test
    void setAndGet_discountModifier_returnsCorrectValue() {
        repo.setModifier(PLAYER, ITEM, 0.9, 0); // 10% off, permanent
        assertEquals(0.9, repo.getModifier(PLAYER, ITEM), 0.0001);
    }

    @Test
    void setAndGet_markupModifier_returnsCorrectValue() {
        repo.setModifier(PLAYER, ITEM, 1.25, 0); // 25% markup
        assertEquals(1.25, repo.getModifier(PLAYER, ITEM), 0.0001);
    }

    @Test
    void setModifier_overwriteExisting_returnsNewValue() {
        repo.setModifier(PLAYER, ITEM, 0.9, 0);
        repo.setModifier(PLAYER, ITEM, 0.8, 0); // overwrite
        assertEquals(0.8, repo.getModifier(PLAYER, ITEM), 0.0001);
    }

    @Test
    void setModifier_permanentDuration_neverExpires() throws InterruptedException {
        repo.setModifier(PLAYER, ITEM, 0.85, 0); // durationMs=0 → permanent
        Thread.sleep(10);
        assertEquals(0.85, repo.getModifier(PLAYER, ITEM), 0.0001);
    }

    @Test
    void twoPlayers_independentModifiers() {
        repo.setModifier(PLAYER,  ITEM, 0.9, 0);
        repo.setModifier(PLAYER2, ITEM, 0.75, 0);
        assertEquals(0.9,  repo.getModifier(PLAYER,  ITEM), 0.0001);
        assertEquals(0.75, repo.getModifier(PLAYER2, ITEM), 0.0001);
    }

    @Test
    void twoItems_independentModifiers() {
        repo.setModifier(PLAYER, ITEM,  0.9, 0);
        repo.setModifier(PLAYER, ITEM2, 1.2, 0);
        assertEquals(0.9, repo.getModifier(PLAYER, ITEM),  0.0001);
        assertEquals(1.2, repo.getModifier(PLAYER, ITEM2), 0.0001);
    }

    // ── Expiry ────────────────────────────────────────────────────────────────

    @Test
    void getModifier_expiredEntry_returnsOne() throws InterruptedException {
        repo.setModifier(PLAYER, ITEM, 0.5, 50); // expires in 50 ms
        Thread.sleep(100);
        assertEquals(1.0, repo.getModifier(PLAYER, ITEM), 0.0001);
    }

    @Test
    void getModifier_notYetExpired_returnsModifier() throws InterruptedException {
        repo.setModifier(PLAYER, ITEM, 0.7, 500); // expires in 500 ms
        Thread.sleep(10);
        assertEquals(0.7, repo.getModifier(PLAYER, ITEM), 0.0001);
    }

    @Test
    void expiredEntry_doesNotAffectOtherItems() throws InterruptedException {
        repo.setModifier(PLAYER, ITEM,  0.9, 50);  // expires soon
        repo.setModifier(PLAYER, ITEM2, 1.1, 0);   // permanent
        Thread.sleep(100);
        assertEquals(1.0, repo.getModifier(PLAYER, ITEM),  0.0001); // expired
        assertEquals(1.1, repo.getModifier(PLAYER, ITEM2), 0.0001); // still active
    }

    // ── removeModifier ────────────────────────────────────────────────────────

    @Test
    void removeModifier_removesFromCache() {
        repo.setModifier(PLAYER, ITEM, 0.9, 0);
        repo.removeModifier(PLAYER, ITEM);
        assertEquals(1.0, repo.getModifier(PLAYER, ITEM), 0.0001);
    }

    @Test
    void removeModifier_doesNotAffectOtherPlayer() {
        repo.setModifier(PLAYER,  ITEM, 0.9, 0);
        repo.setModifier(PLAYER2, ITEM, 0.8, 0);
        repo.removeModifier(PLAYER, ITEM);
        assertEquals(1.0, repo.getModifier(PLAYER,  ITEM), 0.0001);
        assertEquals(0.8, repo.getModifier(PLAYER2, ITEM), 0.0001);
    }

    @Test
    void removeModifier_nonExistentEntry_noException() {
        assertDoesNotThrow(() -> repo.removeModifier(PLAYER, ITEM));
    }

    // ── hasModifier ───────────────────────────────────────────────────────────

    @Test
    void hasModifier_noEntry_returnsFalse() {
        assertFalse(repo.hasModifier(PLAYER, ITEM));
    }

    @Test
    void hasModifier_activeEntry_returnsTrue() {
        repo.setModifier(PLAYER, ITEM, 0.9, 0);
        assertTrue(repo.hasModifier(PLAYER, ITEM));
    }

    @Test
    void hasModifier_exactlyOneModifier_returnsFalse() {
        repo.setModifier(PLAYER, ITEM, 1.0, 0); // modifier == 1.0 means no change
        // hasModifier checks != 1.0, so this should return false
        assertFalse(repo.hasModifier(PLAYER, ITEM));
    }

    // ── evictPlayer ───────────────────────────────────────────────────────────

    @Test
    void evictPlayer_removesAllEntriesForPlayer() {
        repo.setModifier(PLAYER, ITEM,  0.9, 0);
        repo.setModifier(PLAYER, ITEM2, 1.1, 0);
        repo.evictPlayer(PLAYER);
        assertEquals(1.0, repo.getModifier(PLAYER, ITEM),  0.0001);
        assertEquals(1.0, repo.getModifier(PLAYER, ITEM2), 0.0001);
    }

    @Test
    void evictPlayer_doesNotAffectOtherPlayers() {
        repo.setModifier(PLAYER,  ITEM, 0.9, 0);
        repo.setModifier(PLAYER2, ITEM, 0.8, 0);
        repo.evictPlayer(PLAYER);
        assertEquals(1.0, repo.getModifier(PLAYER,  ITEM), 0.0001);
        assertEquals(0.8, repo.getModifier(PLAYER2, ITEM), 0.0001);
    }

    @Test
    void evictPlayer_nonExistentPlayer_noException() {
        assertDoesNotThrow(() -> repo.evictPlayer(UUID.randomUUID()));
    }
}
