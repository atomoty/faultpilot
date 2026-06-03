package io.github.atomoty.faultpilot.adapters.mock;

import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;

import java.util.List;

/**
 * A self-contained mock scenario: the evidence each source adapter should return plus the
 * acceptance contract for the report (specification.md §11).
 *
 * @param expectedRootCauseLabel the root-cause label the report must contain
 * @param requiredEvidenceIds    evidence ids the winning candidate must reference
 */
public record DemoScenario(
        String projectId,
        String environment,
        List<LogEvent> logs,
        List<MetricAnomaly> metrics,
        List<SlowSqlSummary> slowSql,
        List<ChangeEvent> changeEvents,
        String expectedRootCauseLabel,
        List<String> requiredEvidenceIds
) {
}
