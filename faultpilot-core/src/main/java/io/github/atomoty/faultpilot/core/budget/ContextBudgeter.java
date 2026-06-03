package io.github.atomoty.faultpilot.core.budget;

import io.github.atomoty.faultpilot.core.model.BudgetReport;
import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.LogCluster;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;

import java.util.Comparator;
import java.util.List;

/**
 * Trims evidence to the per-category caps before it reaches the model, and records what was kept
 * vs truncated. See specification.md §8.
 *
 * <p>Retention priority (§8.2): higher severity / spike, then higher frequency / duration. Inputs
 * are sorted by that priority and the head is kept.
 */
public class ContextBudgeter {

    private final ContextBudget budget;

    public ContextBudgeter(ContextBudget budget) {
        this.budget = budget;
    }

    public record Budgeted(
            List<LogCluster> logClusters,
            List<MetricAnomaly> metrics,
            List<SlowSqlSummary> slowSql,
            List<ChangeEvent> changeEvents,
            BudgetReport report
    ) {
    }

    public Budgeted apply(
            List<LogCluster> logClusters,
            List<MetricAnomaly> metrics,
            List<SlowSqlSummary> slowSql,
            List<ChangeEvent> changeEvents) {

        // Spikes first, then by count desc.
        List<LogCluster> rankedLogs = logClusters.stream()
                .sorted(Comparator.comparing(LogCluster::spike).reversed()
                        .thenComparing(Comparator.comparingLong(LogCluster::count).reversed()))
                .toList();
        List<SlowSqlSummary> rankedSql = slowSql.stream()
                .sorted(Comparator.comparingLong(SlowSqlSummary::avgDurationMs).reversed())
                .toList();

        List<LogCluster> keptLogs = head(rankedLogs, budget.maxLogClusters());
        List<SlowSqlSummary> keptSql = head(rankedSql, budget.maxSlowSql());
        List<MetricAnomaly> keptMetrics = head(metrics, budget.maxMetrics());
        List<ChangeEvent> keptChanges = head(changeEvents, budget.maxChangeEvents());

        BudgetReport report = new BudgetReport(
                keptLogs.size(), logClusters.size() - keptLogs.size(),
                keptSql.size(), slowSql.size() - keptSql.size(),
                keptMetrics.size(), metrics.size() - keptMetrics.size(),
                keptChanges.size(), changeEvents.size() - keptChanges.size());

        return new Budgeted(keptLogs, keptMetrics, keptSql, keptChanges, report);
    }

    private static <T> List<T> head(List<T> list, int max) {
        return list.size() <= max ? list : List.copyOf(list.subList(0, max));
    }
}
