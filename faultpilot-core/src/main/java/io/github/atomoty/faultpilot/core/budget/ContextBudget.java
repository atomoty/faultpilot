package io.github.atomoty.faultpilot.core.budget;

/**
 * Per-category caps for model input. See specification.md §8.1.
 */
public record ContextBudget(
        int maxLogClusters,
        int maxSlowSql,
        int maxMetrics,
        int maxChangeEvents
) {
    public static ContextBudget defaults() {
        return new ContextBudget(20, 15, 20, 20);
    }
}
