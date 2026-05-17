package dev.fluxshop.trade;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.api.event.TradeCompleteEvent;
import dev.fluxshop.economy.EconomyProvider;
import dev.fluxshop.gui.TradeGui;
import dev.fluxshop.model.TradeOffer;
import dev.fluxshop.storage.TradeRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the full player-trade lifecycle:
 * request → accept → GUI → confirm → atomic exchange → DB log.
 */
public class TradeManager {

    private final FluxShopPlugin   plugin;
    private final TradeRepository  repo;

    /** Pending outgoing requests: sender UUID → receiver UUID */
    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>();

    /** Active trade sessions keyed by both participants' UUIDs */
    private final Map<UUID, TradeOffer> activeTrades = new ConcurrentHashMap<>();

    /** Timestamps (epoch seconds) when a pending request was sent */
    private final Map<UUID, Long> requestTimestamps = new ConcurrentHashMap<>();

    /** Expiry task for request timeouts */
    private BukkitTask timeoutTask;

    private int getRequestTimeoutSecs() {
        return plugin.getConfigManager().getInt("trade.request-timeout-seconds", 60);
    }

    public TradeManager(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.repo   = new TradeRepository(plugin);
    }

    public void start() {
        // Check for timed-out requests every 10 seconds
        timeoutTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::expireRequests, 200L, 200L);
    }

    public void stop() {
        if (timeoutTask != null) timeoutTask.cancel();
    }

    // ── Request flow ──────────────────────────────────────────────────────────

    /**
     * Sends a trade request from one player to another.
     *
     * @return null on success, or an error message
     */
    public String sendRequest(Player sender, Player target) {
        if (sender.equals(target)) return "You cannot trade with yourself.";
        if (activeTrades.containsKey(sender.getUniqueId())) return "You are already in a trade.";
        if (activeTrades.containsKey(target.getUniqueId())) return target.getName() + " is already in a trade.";
        if (pendingRequests.containsKey(sender.getUniqueId())) return "You already have a pending trade request.";
        if (pendingRequests.containsKey(target.getUniqueId())) return target.getName() + " already has a pending trade request.";

        pendingRequests.put(sender.getUniqueId(), target.getUniqueId());
        pendingRequests.put(target.getUniqueId(), sender.getUniqueId()); // mark target as pending too
        long nowSec = System.currentTimeMillis() / 1000;
        requestTimestamps.put(sender.getUniqueId(), nowSec);
        requestTimestamps.put(target.getUniqueId(), nowSec);

        int timeout = plugin.getConfigManager().getInt("trade.request-timeout-seconds", 60);
        plugin.getMessageManager().send(target, "trade.request-received", "player", sender.getName());
        plugin.getMessageManager().send(sender, "trade.request-sent", "player", target.getName(), "timeout", timeout);
        return null;
    }

    /**
     * Accepts a pending trade request, opening the trade GUI for both players.
     *
     * @return null on success, or an error message
     */
    public String acceptRequest(Player receiver) {
        UUID senderUuid = pendingRequests.get(receiver.getUniqueId());
        if (senderUuid == null) return "You have no pending trade request.";

        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender == null) {
            pendingRequests.remove(receiver.getUniqueId());
            pendingRequests.remove(senderUuid);
            return "The other player is no longer online.";
        }

        pendingRequests.remove(receiver.getUniqueId());
        pendingRequests.remove(senderUuid);
        requestTimestamps.remove(receiver.getUniqueId());
        requestTimestamps.remove(senderUuid);

        // Create the trade offer
        TradeOffer offer = new TradeOffer();
        offer.setPlayerA(sender.getUniqueId());
        offer.setPlayerAName(sender.getName());
        offer.setPlayerB(receiver.getUniqueId());
        offer.setPlayerBName(receiver.getName());
        offer.setStatus(TradeOffer.Status.ACTIVE);
        offer.setCurrency(plugin.getEconomyRegistry().getDefault() != null
            ? plugin.getEconomyRegistry().getDefault().getId() : "vault");
        offer.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        activeTrades.put(sender.getUniqueId(), offer);
        activeTrades.put(receiver.getUniqueId(), offer);

        // Open GUI for both players (must run on main thread)
        // Use openReplace so GuiManager tracks it — click events and quit cleanup depend on this.
        TradeGui gui = new TradeGui(plugin, offer);
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getGuiManager().openReplace(sender,   gui);
            plugin.getGuiManager().openReplace(receiver, gui);
        });

        return null;
    }

    /** Cancels a pending request or active trade for this player. */
    public void cancel(Player player) {
        UUID uuid = player.getUniqueId();
        UUID pendingOther = pendingRequests.remove(uuid);
        if (pendingOther != null) {
            pendingRequests.remove(pendingOther);
            requestTimestamps.remove(pendingOther);
        }
        requestTimestamps.remove(uuid);

        TradeOffer offer = activeTrades.remove(uuid);
        if (offer != null) {
            offer.setStatus(TradeOffer.Status.CANCELLED);
            // Remove the other participant too
            UUID other = offer.getPlayerA().equals(uuid) ? offer.getPlayerB() : offer.getPlayerA();
            activeTrades.remove(other);

            // Return any items placed in the GUI back to both players
            returnItems(offer, offer.getPlayerA(), offer.getItemsA());
            returnItems(offer, offer.getPlayerB(), offer.getItemsB());

            Player otherPlayer = Bukkit.getPlayer(other);
            if (otherPlayer != null) {
                otherPlayer.closeInventory();
                plugin.getMessageManager().send(otherPlayer, "trade.cancelled-other", "player", player.getName());
            }
        }
        plugin.getMessageManager().send(player, "trade.cancelled-self");
    }

    // ── Confirmation & execution ──────────────────────────────────────────────

    /**
     * Called when a player clicks the confirm button in the trade GUI.
     * Executes the trade atomically when both players have confirmed.
     */
    public void confirm(Player player) {
        TradeOffer offer = activeTrades.get(player.getUniqueId());
        if (offer == null || offer.getStatus() == TradeOffer.Status.CANCELLED) return;

        boolean isA = offer.getPlayerA().equals(player.getUniqueId());
        if (isA) offer.setConfirmedA(true);
        else     offer.setConfirmedB(true);

        if (offer.getStatus() == TradeOffer.Status.BOTH_CONFIRMED) {
            executeTrade(offer);
        } else {
            // Notify the other player
            UUID otherUuid = isA ? offer.getPlayerB() : offer.getPlayerA();
            Player other   = Bukkit.getPlayer(otherUuid);
            if (other != null) plugin.getMessageManager().send(other, "trade.partner-confirmed", "player", player.getName());
        }
    }

    public TradeOffer getActiveTrade(UUID uuid) {
        return activeTrades.get(uuid);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void executeTrade(TradeOffer offer) {
        offer.setStatus(TradeOffer.Status.COMPLETE);

        Player playerA = Bukkit.getPlayer(offer.getPlayerA());
        Player playerB = Bukkit.getPlayer(offer.getPlayerB());

        activeTrades.remove(offer.getPlayerA());
        activeTrades.remove(offer.getPlayerB());

        // Validate both still online
        if (playerA == null || playerB == null) {
            // Rollback — return items to whoever is still online
            if (playerA != null) returnItems(offer, offer.getPlayerA(), offer.getItemsA());
            if (playerB != null) returnItems(offer, offer.getPlayerB(), offer.getItemsB());
            return;
        }

        // Validate economy balances
        EconomyProvider eco = plugin.getEconomyRegistry().get(offer.getCurrency());
        if (eco != null) {
            if (offer.getCurrencyAmountA() > 0 && !eco.has(offer.getPlayerA(), offer.getCurrencyAmountA())) {
                abortTrade(playerA, playerB, offer, playerA.getName() + " cannot afford the trade.");
                return;
            }
            if (offer.getCurrencyAmountB() > 0 && !eco.has(offer.getPlayerB(), offer.getCurrencyAmountB())) {
                abortTrade(playerA, playerB, offer, playerB.getName() + " cannot afford the trade.");
                return;
            }
        }

        // Execute economy exchange — withdraw both first, then deposit, to allow clean rollback
        if (eco != null) {
            boolean wdA = offer.getCurrencyAmountA() <= 0
                || eco.withdraw(offer.getPlayerA(), offer.getCurrencyAmountA());
            boolean wdB = offer.getCurrencyAmountB() <= 0
                || eco.withdraw(offer.getPlayerB(), offer.getCurrencyAmountB());

            if (!wdA || !wdB) {
                // Roll back any successful withdrawal before aborting
                if (wdA && offer.getCurrencyAmountA() > 0)
                    eco.deposit(offer.getPlayerA(), offer.getCurrencyAmountA());
                if (wdB && offer.getCurrencyAmountB() > 0)
                    eco.deposit(offer.getPlayerB(), offer.getCurrencyAmountB());
                abortTrade(playerA, playerB, offer, "Economy error: currency transfer failed.");
                return;
            }

            // Both withdrawals succeeded — now deposit
            if (offer.getCurrencyAmountA() > 0) eco.deposit(offer.getPlayerB(), offer.getCurrencyAmountA());
            if (offer.getCurrencyAmountB() > 0) eco.deposit(offer.getPlayerA(), offer.getCurrencyAmountB());
        }

        // Close GUIs and exchange items on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            playerA.closeInventory();
            playerB.closeInventory();

            // Give A's items to B and vice versa
            giveItems(playerA, offer.getItemsB());
            giveItems(playerB, offer.getItemsA());

            plugin.getMessageManager().send(playerA, "trade.complete");
            plugin.getMessageManager().send(playerB, "trade.complete");
            plugin.getSoundManager().play(playerA, "trade-complete");
            plugin.getSoundManager().play(playerB, "trade-complete");
            plugin.getParticleManager().spawn(playerA, "trade-complete");
            plugin.getParticleManager().spawn(playerB, "trade-complete");

            // Fire event
            Bukkit.getPluginManager().callEvent(new TradeCompleteEvent(offer));

            // Log to DB
            repo.record(offer);
        });
    }

    private void abortTrade(Player playerA, Player playerB, TradeOffer offer, String reason) {
        offer.setStatus(TradeOffer.Status.CANCELLED);
        returnItems(offer, offer.getPlayerA(), offer.getItemsA());
        returnItems(offer, offer.getPlayerB(), offer.getItemsB());
        playerA.closeInventory();
        playerB.closeInventory();
        plugin.getMessageManager().send(playerA, "trade.failed", "reason", reason);
        plugin.getMessageManager().send(playerB, "trade.failed", "reason", reason);
    }

    private void giveItems(Player player, List<ItemStack> items) {
        if (items.isEmpty()) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private void returnItems(TradeOffer offer, UUID uuid, List<ItemStack> items) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) giveItems(player, items);
    }

    /** Returns {@code true} if this player is currently in an active trade session. */
    public boolean isInTrade(UUID uuid) {
        return activeTrades.containsKey(uuid);
    }

    private void expireRequests() {
        long nowSec = System.currentTimeMillis() / 1000;
        new java.util.ArrayList<>(requestTimestamps.keySet()).forEach(uuid -> {
            Long sent = requestTimestamps.get(uuid);
            if (sent != null && nowSec - sent > getRequestTimeoutSecs()) {
                UUID other = pendingRequests.remove(uuid);
                requestTimestamps.remove(uuid);
                if (other != null) {
                    pendingRequests.remove(other);
                    requestTimestamps.remove(other);
                    // sendMessage / getPlayer must run on the main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player expired = Bukkit.getPlayer(uuid);
                        Player otherP  = Bukkit.getPlayer(other);
                        if (expired != null) plugin.getMessageManager().send(expired, "trade.request-expired-self");
                        if (otherP  != null) plugin.getMessageManager().send(otherP, "trade.request-expired-other",
                            "player", expired != null ? expired.getName() : "a player");
                    });
                }
            }
        });
    }
}
