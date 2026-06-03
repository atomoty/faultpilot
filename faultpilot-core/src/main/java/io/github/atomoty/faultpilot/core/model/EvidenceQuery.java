package io.github.atomoty.faultpilot.core.model;

import java.time.Instant;

/**
 * Query passed to every evidence source adapter. Carries the enforced time range and result cap.
 */
public record EvidenceQuery(
        String projectId,
        String environment,
        Instant from,
        Instant to,
        int maxResults
) {
}
