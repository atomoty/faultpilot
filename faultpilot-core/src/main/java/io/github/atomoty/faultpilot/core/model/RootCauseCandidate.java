package io.github.atomoty.faultpilot.core.model;

import java.util.List;

/**
 * A candidate root cause. {@code strength} is computed by rules, not by the model (specification.md §10).
 * {@code evidenceIds} must be non-empty: a candidate with no evidence is rejected (specification.md §11).
 *
 * @param label       a stable machine label (e.g. "slow-sql-pool-contention") used for acceptance checks
 * @param title       human-readable title
 * @param explanation model-generated explanation, may be null in a rule-only fallback report
 * @param strength    rule-computed evidence strength
 * @param evidenceIds ids of the evidence supporting this candidate
 */
public record RootCauseCandidate(
        String label,
        String title,
        String explanation,
        EvidenceStrength strength,
        List<String> evidenceIds
) {
    public RootCauseCandidate {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
