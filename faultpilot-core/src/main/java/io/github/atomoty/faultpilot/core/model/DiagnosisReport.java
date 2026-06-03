package io.github.atomoty.faultpilot.core.model;

import java.time.Instant;
import java.util.List;

/**
 * The structured report returned to the user. See design.md §11, §20.3.
 *
 * @param ruleFallback whether this report was produced by the rule-only fallback (model unavailable)
 * @param disclaimer   mandatory human-verification notice (specification.md §3.3)
 */
public record DiagnosisReport(
        String diagnosisId,
        String projectId,
        String environment,
        String summary,
        List<TimelineEntry> timeline,
        List<RootCauseCandidate> rootCauseCandidates,
        List<String> recommendedActions,
        List<Evidence> evidence,
        List<String> unavailableSources,
        BudgetReport budget,
        boolean ruleFallback,
        String disclaimer,
        Instant createdAt
) {
    public static final String DISCLAIMER =
            "AI 生成的排障建议,仅供人工核验。请在执行任何变更前复查证据。";

    public DiagnosisReport {
        timeline = nullToEmpty(timeline);
        rootCauseCandidates = nullToEmpty(rootCauseCandidates);
        recommendedActions = nullToEmpty(recommendedActions);
        evidence = nullToEmpty(evidence);
        unavailableSources = nullToEmpty(unavailableSources);
    }

    private static <T> List<T> nullToEmpty(List<T> in) {
        return in == null ? List.of() : List.copyOf(in);
    }
}
