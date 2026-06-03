package io.github.atomoty.faultpilot.core.model;

import java.time.Instant;

/**
 * A single point on the incident timeline. See design.md §11 response shape.
 */
public record TimelineEntry(
        Instant at,
        String category,
        String description,
        String evidenceId
) {
}
