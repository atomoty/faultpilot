package io.github.atomoty.faultpilot.core.model;

import java.time.Instant;
import java.util.Map;

/**
 * A normalized log event produced by any log source adapter. See design.md §3.2.
 */
public record LogEvent(
        String projectId,
        String environment,
        Instant occurredAt,
        String level,
        String logger,
        String traceId,
        String message,
        String stackTrace,
        Map<String, String> attributes
) {
    public LogEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
