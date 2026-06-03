package io.github.atomoty.faultpilot.server.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request body for {@code POST /api/v1/diagnoses}. See design.md §11.
 */
public record DiagnosisRequestDto(
        @NotBlank String projectId,
        @NotBlank String environment,
        @NotBlank String question,
        @NotNull Instant from,
        @NotNull Instant to
) {
}
