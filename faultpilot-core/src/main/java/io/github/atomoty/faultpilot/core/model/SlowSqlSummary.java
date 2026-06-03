package io.github.atomoty.faultpilot.core.model;

import java.time.Instant;

/**
 * Aggregated slow-SQL statistics for a single parameterized template. See design.md §3.5.
 * Only parameterized templates are carried; never real business parameters.
 */
public record SlowSqlSummary(
        String evidenceId,
        String sqlTemplate,
        long occurrences,
        long avgDurationMs,
        long maxDurationMs,
        Instant firstSeen,
        String source
) {
}
