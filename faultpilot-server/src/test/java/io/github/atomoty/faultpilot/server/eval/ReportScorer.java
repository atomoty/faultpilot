package io.github.atomoty.faultpilot.server.eval;

import io.github.atomoty.faultpilot.adapters.mock.DemoScenario;
import io.github.atomoty.faultpilot.core.model.DiagnosisReport;
import io.github.atomoty.faultpilot.core.model.Evidence;
import io.github.atomoty.faultpilot.core.model.RootCauseCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Grades a produced {@link DiagnosisReport} against a scenario's known-correct answer.
 *
 * <p>The checks are deliberately about things a wrong answer would get wrong: naming the right root
 * cause, citing the evidence that actually supports it, and not inventing evidence ids. Wording is
 * not graded — models phrase things differently and that is fine.
 */
public final class ReportScorer {

    private ReportScorer() {
    }

    public static EvalResult score(DemoScenario scenario, DiagnosisReport report, long elapsedMillis) {
        List<String> notes = new ArrayList<>();
        String name = scenario.projectId() + "/" + scenario.environment();

        List<String> labels = report.rootCauseCandidates().stream()
                .map(RootCauseCandidate::label)
                .toList();

        boolean rootCauseHit = labels.contains(scenario.expectedRootCauseLabel());
        if (!rootCauseHit) {
            notes.add("expected root cause '%s' but got %s"
                    .formatted(scenario.expectedRootCauseLabel(), labels));
        }

        // Evidence cited by the candidate that matches the expected label (if it is present at all).
        Set<String> citedByExpected = report.rootCauseCandidates().stream()
                .filter(c -> scenario.expectedRootCauseLabel().equals(c.label()))
                .flatMap(c -> c.evidenceIds().stream())
                .collect(Collectors.toSet());
        List<String> missing = scenario.requiredEvidenceIds().stream()
                .filter(id -> !citedByExpected.contains(id))
                .toList();
        boolean evidenceCited = rootCauseHit && missing.isEmpty();
        if (rootCauseHit && !missing.isEmpty()) {
            notes.add("candidate '%s' did not cite required evidence %s (cited %s)"
                    .formatted(scenario.expectedRootCauseLabel(), missing, citedByExpected));
        }

        // Every cited id must exist in the report's evidence list. The service already drops
        // candidates whose ids resolve to nothing, so a violation here is a real regression.
        Set<String> availableIds = report.evidence().stream()
                .map(Evidence::evidenceId)
                .collect(Collectors.toSet());
        List<String> fabricated = report.rootCauseCandidates().stream()
                .flatMap(c -> c.evidenceIds().stream())
                .filter(id -> !availableIds.contains(id))
                .distinct()
                .toList();
        boolean noFabricatedIds = fabricated.isEmpty();
        if (!noFabricatedIds) {
            notes.add("candidates referenced evidence ids not present in the report: " + fabricated);
        }

        boolean modelAnswered = !report.ruleFallback();
        if (!modelAnswered) {
            notes.add("report fell back to rules — the model did not answer (unavailableSources=%s)"
                    .formatted(report.unavailableSources()));
        }

        boolean summaryPresent = report.summary() != null && !report.summary().isBlank();
        if (!summaryPresent) {
            notes.add("summary was blank");
        }

        return new EvalResult(name, rootCauseHit, evidenceCited, noFabricatedIds,
                modelAnswered, summaryPresent, notes, labels, elapsedMillis);
    }
}
