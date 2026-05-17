package dev.fluxshop.model;

import dev.fluxshop.storage.AnalyticsRepository.ItemStat;
import dev.fluxshop.storage.AnalyticsRepository.PlayerStat;

import java.util.List;
import java.util.Map;

/**
 * A point-in-time snapshot of aggregated shop analytics data.
 *
 * <p>Returned by {@link dev.fluxshop.storage.AnalyticsRepository#loadSnapshot(long, int)}
 * as a single typed DTO instead of five separate {@link java.util.concurrent.CompletableFuture}s.
 *
 * @param generatedAt     epoch-millis when the snapshot was assembled
 * @param windowMs        the look-back window used for time-bound stats (0 = all-time)
 * @param topBought       top N items by purchase quantity within the window
 * @param topSold         top N items by sale quantity within the window
 * @param revenueByCurrency total buy revenue grouped by currency id (all-time)
 * @param transactionCount  number of transactions within the window
 * @param economyHealth   buy/sell balance score 0–200 (100 = balanced)
 * @param topBuyers       top N buyers by total spend within the window
 */
public record AnalyticsSnapshot(
        long               generatedAt,
        long               windowMs,
        List<ItemStat>     topBought,
        List<ItemStat>     topSold,
        Map<String, Double> revenueByCurrency,
        long               transactionCount,
        double             economyHealth,
        List<PlayerStat>   topBuyers
) {
    /** Compact constructor — defensive copy of mutable collections. */
    public AnalyticsSnapshot {
        topBought          = List.copyOf(topBought);
        topSold            = List.copyOf(topSold);
        revenueByCurrency  = Map.copyOf(revenueByCurrency);
        topBuyers          = List.copyOf(topBuyers);
    }
}
