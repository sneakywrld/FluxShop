package dev.fluxshop.model;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an active player-to-player trade session.
 */
public class TradeOffer {

    public enum Status { PENDING, ACTIVE, CONFIRMED_A, CONFIRMED_B, BOTH_CONFIRMED, COMPLETE, CANCELLED }

    // ── Participants ──────────────────────────────────────────────────────────
    private UUID   playerA;
    private String playerAName;
    private UUID   playerB;
    private String playerBName;

    // ── Offered items ─────────────────────────────────────────────────────────
    private final List<ItemStack> itemsA = new ArrayList<>();
    private final List<ItemStack> itemsB = new ArrayList<>();

    // ── Offered currency ──────────────────────────────────────────────────────
    private double currencyAmountA = 0.0;
    private double currencyAmountB = 0.0;
    private String currency;

    // ── State ─────────────────────────────────────────────────────────────────
    private Status  status    = Status.PENDING;
    private Instant createdAt = Instant.now();
    private Instant expiresAt;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID   getPlayerA()          { return playerA; }
    public void   setPlayerA(UUID u)    { this.playerA = u; }

    public String getPlayerAName()      { return playerAName; }
    public void   setPlayerAName(String n){ this.playerAName = n; }

    public UUID   getPlayerB()          { return playerB; }
    public void   setPlayerB(UUID u)    { this.playerB = u; }

    public String getPlayerBName()      { return playerBName; }
    public void   setPlayerBName(String n){ this.playerBName = n; }

    public List<ItemStack> getItemsA()  { return itemsA; }
    public List<ItemStack> getItemsB()  { return itemsB; }

    public double getCurrencyAmountA()     { return currencyAmountA; }
    public void   setCurrencyAmountA(double a){ this.currencyAmountA = a; }

    public double getCurrencyAmountB()     { return currencyAmountB; }
    public void   setCurrencyAmountB(double b){ this.currencyAmountB = b; }

    public String getCurrency()            { return currency; }
    public void   setCurrency(String c)    { this.currency = c; }

    public Status  getStatus()             { return status; }
    public void    setStatus(Status s)     { this.status = s; }

    public Instant getCreatedAt()          { return createdAt; }
    public Instant getExpiresAt()          { return expiresAt; }
    public void    setExpiresAt(Instant i) { this.expiresAt = i; }

    /** Resets confirmations of both players (called when either player modifies their offer). */
    public void resetConfirmations() {
        if (status == Status.CONFIRMED_A || status == Status.CONFIRMED_B || status == Status.BOTH_CONFIRMED) {
            status = Status.ACTIVE;
        }
    }

    public boolean isConfirmedByA() {
        return status == Status.CONFIRMED_A || status == Status.BOTH_CONFIRMED;
    }

    public boolean isConfirmedByB() {
        return status == Status.CONFIRMED_B || status == Status.BOTH_CONFIRMED;
    }

    /** Mark player A as confirmed. Advances status accordingly. */
    public void setConfirmedA(boolean confirmed) {
        if (!confirmed) { resetConfirmations(); return; }
        if (isConfirmedByB()) status = Status.BOTH_CONFIRMED;
        else                  status = Status.CONFIRMED_A;
    }

    /** Mark player B as confirmed. Advances status accordingly. */
    public void setConfirmedB(boolean confirmed) {
        if (!confirmed) { resetConfirmations(); return; }
        if (isConfirmedByA()) status = Status.BOTH_CONFIRMED;
        else                  status = Status.CONFIRMED_B;
    }
}
