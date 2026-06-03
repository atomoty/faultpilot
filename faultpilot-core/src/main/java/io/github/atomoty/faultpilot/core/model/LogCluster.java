package io.github.atomoty.faultpilot.core.model;

import java.time.Instant;
import java.util.List;

/**
 * A cluster of similar log events. See development-plan.md §5, specification.md §7.4.
 *
 * @param evidenceId      stable id used to reference this cluster from a root-cause candidate
 * @param clusterKey      the normalized clustering key
 * @param exceptionClass  normalized exception class, may be null for non-exception logs
 * @param messageTemplate normalized message template
 * @param topStackFrame   top normalized stack frame, may be null
 * @param level           dominant log level
 * @param count           number of events in the current window
 * @param baselineCount   number of events in the baseline window
 * @param spike           whether this cluster is flagged as a spike
 * @param firstSeen       earliest occurrence in the cluster
 * @param lastSeen        latest occurrence in the cluster
 * @param sample          a representative (sanitized) sample event
 */
public record LogCluster(
        String evidenceId,
        String clusterKey,
        String exceptionClass,
        String messageTemplate,
        String topStackFrame,
        String level,
        long count,
        long baselineCount,
        boolean spike,
        Instant firstSeen,
        Instant lastSeen,
        LogEvent sample
) {
    public static List<LogCluster> none() {
        return List.of();
    }
}
