package io.github.atomoty.faultpilot.core.pipeline;

import io.github.atomoty.faultpilot.core.adapter.ChangeEventSource;
import io.github.atomoty.faultpilot.core.adapter.DatabaseHealthSourceAdapter;
import io.github.atomoty.faultpilot.core.adapter.LogSourceAdapter;
import io.github.atomoty.faultpilot.core.adapter.MetricSourceAdapter;
import io.github.atomoty.faultpilot.core.adapter.SlowSqlSourceAdapter;
import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.DatabaseHealthSnapshot;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Orchestrates evidence source adapters in parallel with a per-source timeout. Any source that
 * times out or throws is recorded as unavailable and contributes no evidence — the diagnosis
 * proceeds with whatever was collected (specification.md §9).
 *
 * <p><b>Timeout boundary (important for new source adapters).</b> {@code perSourceTimeout} is a
 * <i>soft</i> timeout: it completes the waiting future and frees the diagnosis, but it does NOT
 * interrupt the underlying work — a {@link CompletableFuture} cannot interrupt a task already
 * running on the executor (a JDK limitation). The bounded executor then sheds further submissions
 * (degraded to "rejected") rather than queuing unboundedly. Therefore every source adapter MUST
 * enforce its own hard timeout on blocking I/O (e.g. JDBC sources set login/query timeouts; local
 * files are bounded in size). Do not add a source that can block indefinitely and rely on this
 * collector to cancel it — it cannot.
 */
public class EvidenceCollector {

    private final List<LogSourceAdapter> logSources;
    private final List<MetricSourceAdapter> metricSources;
    private final List<SlowSqlSourceAdapter> slowSqlSources;
    private final List<ChangeEventSource> changeEventSources;
    private final List<DatabaseHealthSourceAdapter> databaseHealthSources;
    private final Duration perSourceTimeout;
    private final ExecutorService executor;

    public EvidenceCollector(
            List<LogSourceAdapter> logSources,
            List<MetricSourceAdapter> metricSources,
            List<SlowSqlSourceAdapter> slowSqlSources,
            List<ChangeEventSource> changeEventSources,
            List<DatabaseHealthSourceAdapter> databaseHealthSources,
            Duration perSourceTimeout,
            ExecutorService executor) {
        this.logSources = logSources;
        this.metricSources = metricSources;
        this.slowSqlSources = slowSqlSources;
        this.changeEventSources = changeEventSources;
        this.databaseHealthSources = databaseHealthSources;
        this.perSourceTimeout = perSourceTimeout;
        this.executor = executor;
    }

    public CollectedEvidence collect(EvidenceQuery query) {
        List<String> unavailable = new CopyOnWriteArrayList<>();

        CompletableFuture<List<LogEvent>> logs =
                gather("log", logSources, a -> a.query(query), unavailable);
        CompletableFuture<List<MetricAnomaly>> metrics =
                gather("metric", metricSources, a -> a.query(query), unavailable);
        CompletableFuture<List<SlowSqlSummary>> slowSql =
                gather("slow-sql", slowSqlSources, a -> a.query(query), unavailable);
        CompletableFuture<List<ChangeEvent>> changes =
                gather("change-event", changeEventSources, a -> a.query(query), unavailable);
        // A health adapter returns a single snapshot. A real unavailable database is kept and
        // recorded as unavailable; "no database configured" is a routing sentinel and is ignored.
        CompletableFuture<List<DatabaseHealthSnapshot>> dbHealth =
                gatherDatabaseHealth(query, unavailable);

        CompletableFuture.allOf(logs, metrics, slowSql, changes, dbHealth).join();

        return new CollectedEvidence(
                logs.join(), metrics.join(), slowSql.join(), changes.join(), dbHealth.join(),
                List.copyOf(unavailable));
    }

    /** Run all adapters of one kind in parallel; collect results, recording failures as unavailable. */
    private <A, R> CompletableFuture<List<R>> gather(
            String kind, List<A> adapters, Function<A, List<R>> call, List<String> unavailable) {

        List<CompletableFuture<List<R>>> futures = new ArrayList<>();
        for (int i = 0; i < adapters.size(); i++) {
            A adapter = adapters.get(i);
            String label = kind + ":" + adapter.getClass().getSimpleName();
            CompletableFuture<List<R>> f;
            try {
                // Soft timeout: completes this future on expiry but does NOT interrupt the running
                // task (see class javadoc). The adapter itself must bound its blocking I/O.
                f = CompletableFuture
                        .supplyAsync(() -> call.apply(adapter), executor)
                        .orTimeout(perSourceTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> {
                            unavailable.add(label + (ex.getCause() instanceof TimeoutException
                                    || ex instanceof TimeoutException ? " (timeout)" : " (error)"));
                            return List.of();
                        });
            } catch (RejectedExecutionException rejected) {
                // Pool saturated (e.g. earlier sources stuck): degrade this source instead of failing.
                unavailable.add(label + " (rejected)");
                f = CompletableFuture.completedFuture(List.of());
            }
            futures.add(f);
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> {
                    List<R> all = new ArrayList<>();
                    for (CompletableFuture<List<R>> f : futures) {
                        all.addAll(f.join());
                    }
                    return all;
                });
    }

    private CompletableFuture<List<DatabaseHealthSnapshot>> gatherDatabaseHealth(
            EvidenceQuery query, List<String> unavailable) {

        return gather("db-health", databaseHealthSources, adapter -> {
            DatabaseHealthSnapshot snapshot = adapter.query(query);
            if (snapshot == null || snapshot.isNoDatabaseConfigured()) {
                return List.of();
            }
            if (!snapshot.available()) {
                unavailable.add("db-health:" + adapter.getClass().getSimpleName() + " (error)");
            }
            return List.of(snapshot);
        }, unavailable);
    }
}
