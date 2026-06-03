package io.github.atomoty.faultpilot.core.model;

/**
 * Auditable result of context budgeting: how much evidence was kept vs truncated.
 * Surfaced in report metadata (specification.md §8.2).
 */
public record BudgetReport(
        int logClustersKept,
        int logClustersTruncated,
        int slowSqlKept,
        int slowSqlTruncated,
        int metricsKept,
        int metricsTruncated,
        int changeEventsKept,
        int changeEventsTruncated
) {
    public static BudgetReport empty() {
        return new BudgetReport(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public boolean anyTruncated() {
        return logClustersTruncated + slowSqlTruncated + metricsTruncated + changeEventsTruncated > 0;
    }
}
