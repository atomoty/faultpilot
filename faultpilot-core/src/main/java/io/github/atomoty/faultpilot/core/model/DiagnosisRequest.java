package io.github.atomoty.faultpilot.core.model;

import java.time.Instant;

/**
 * A user-initiated diagnosis request. See design.md §11.
 */
public record DiagnosisRequest(
        String projectId,
        String environment,
        String question,
        Instant from,
        Instant to
) {
}
