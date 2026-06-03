package io.github.atomoty.faultpilot.core.rule;

import io.github.atomoty.faultpilot.core.model.LogCluster;
import io.github.atomoty.faultpilot.core.model.RootCauseCandidate;
import io.github.atomoty.faultpilot.core.model.TimelineEntry;

import java.util.List;

/**
 * Output of {@link RuleAnalyzer}: clustered logs, the timeline and rule-derived root-cause candidates.
 */
public record RuleAnalysis(
        List<LogCluster> logClusters,
        List<TimelineEntry> timeline,
        List<RootCauseCandidate> ruleCandidates
) {
}
