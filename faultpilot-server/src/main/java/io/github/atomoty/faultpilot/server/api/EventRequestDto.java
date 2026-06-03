package io.github.atomoty.faultpilot.server.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Request body for {@code POST /api/v1/events}. See design.md §3.4, §12.
 */
public record EventRequestDto(
        @NotBlank String projectId,
        @NotBlank String environment,
        @NotBlank String type,
        @NotNull Instant occurredAt,
        Map<String, String> attributes
) {
}
