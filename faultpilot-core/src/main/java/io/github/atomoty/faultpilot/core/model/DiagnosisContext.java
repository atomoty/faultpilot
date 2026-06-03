package io.github.atomoty.faultpilot.core.model;

import java.util.List;

/**
 * The structured, sanitized, budgeted context handed to the diagnosis model.
 * The model only ever sees this — never raw logs. See specification.md §6, §20.2.
 */
public record DiagnosisContext(
        DiagnosisRequest request,
        List<LogCluster> logClusters,
        List<MetricAnomaly> metricAnomalies,
        List<SlowSqlSummary> slowSqlSummaries,
        List<ChangeEvent> changeEvents,
        List<DatabaseHealthSnapshot> databaseHealth,
        List<TimelineEntry> timeline,
        List<RootCauseCandidate> ruleCandidates,
        List<String> unavailableSources,
        BudgetReport budget
) {
    public DiagnosisContext {
        logClusters = nullToEmpty(logClusters);
        metricAnomalies = nullToEmpty(metricAnomalies);
        slowSqlSummaries = nullToEmpty(slowSqlSummaries);
        changeEvents = nullToEmpty(changeEvents);
        databaseHealth = nullToEmpty(databaseHealth);
        timeline = nullToEmpty(timeline);
        ruleCandidates = nullToEmpty(ruleCandidates);
        unavailableSources = nullToEmpty(unavailableSources);
    }

    private static <T> List<T> nullToEmpty(List<T> in) {
        return in == null ? List.of() : List.copyOf(in);
    }
}
