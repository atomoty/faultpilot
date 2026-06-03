package io.github.atomoty.faultpilot.core.pipeline;

import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.DatabaseHealthSnapshot;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;

import java.util.List;

/**
 * Raw evidence gathered from all sources, plus the names of sources that were unavailable
 * (timed out or threw). A failed source never aborts the diagnosis (specification.md §9).
 */
public record CollectedEvidence(
        List<LogEvent> logs,
        List<MetricAnomaly> metrics,
        List<SlowSqlSummary> slowSql,
        List<ChangeEvent> changeEvents,
        List<DatabaseHealthSnapshot> databaseHealth,
        List<String> unavailableSources
) {
}
