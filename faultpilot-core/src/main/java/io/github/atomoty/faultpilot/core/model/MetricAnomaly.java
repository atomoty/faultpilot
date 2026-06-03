package io.github.atomoty.faultpilot.core.model;

import java.time.Instant;

/**
 * A summarized metric anomaly (e.g. P95 latency increase, connection-pool waits). See design.md §3.3.
 */
public record MetricAnomaly(
        String evidenceId,
        String metric,
        String description,
        double baselineValue,
        double currentValue,
        String unit,
        Instant observedAt
) {
}
