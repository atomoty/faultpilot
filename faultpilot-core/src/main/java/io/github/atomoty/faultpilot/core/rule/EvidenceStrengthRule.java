package io.github.atomoty.faultpilot.core.rule;

import io.github.atomoty.faultpilot.core.model.EvidenceStrength;

/**
 * Computes {@link EvidenceStrength} from deterministic inputs only. See specification.md §10.
 */
public final class EvidenceStrengthRule {

    private EvidenceStrengthRule() {
    }

    /**
     * @param independentSources number of distinct evidence sources backing the candidate
     * @param sharedTraceId      whether the supporting evidence shares a traceId
     * @param explicitRuleHit    whether an explicit rule (e.g. spike, deployment window) fired
     */
    public static EvidenceStrength compute(int independentSources, boolean sharedTraceId, boolean explicitRuleHit) {
        if (independentSources >= 2 && (sharedTraceId || explicitRuleHit)) {
            return EvidenceStrength.STRONG;
        }
        if (independentSources >= 2) {
            return EvidenceStrength.MODERATE;
        }
        return EvidenceStrength.WEAK;
    }
}
