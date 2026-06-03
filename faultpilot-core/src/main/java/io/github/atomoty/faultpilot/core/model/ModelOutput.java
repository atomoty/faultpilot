package io.github.atomoty.faultpilot.core.model;

import java.util.List;

/**
 * The model's contribution to a report. The model proposes a narrative and candidate root causes
 * referencing existing evidence ids; it does NOT assign {@link EvidenceStrength} (rules do that)
 * and does NOT emit self-rated probabilities. See specification.md §3, §10.
 */
public record ModelOutput(
        String summary,
        List<Candidate> rootCauseCandidates,
        List<String> recommendedActions
) {
    public ModelOutput {
        rootCauseCandidates = rootCauseCandidates == null ? List.of() : List.copyOf(rootCauseCandidates);
        recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
    }

    /**
     * A model-proposed root cause. {@code evidenceIds} must reference evidence already present in the
     * context; candidates with no resolvable evidence are dropped by the service.
     */
    public record Candidate(
            String label,
            String title,
            String explanation,
            List<String> evidenceIds
    ) {
        public Candidate {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }
}
