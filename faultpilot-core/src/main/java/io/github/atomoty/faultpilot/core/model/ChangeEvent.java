package io.github.atomoty.faultpilot.core.model;

import java.time.Instant;
import java.util.Map;

/**
 * A change/lifecycle event: deployment, rollback, config change, job lifecycle, etc.
 * See design.md §3.4.
 */
public record ChangeEvent(
        String evidenceId,
        String projectId,
        String environment,
        String type,
        Instant occurredAt,
        Map<String, String> attributes
) {
    public ChangeEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
