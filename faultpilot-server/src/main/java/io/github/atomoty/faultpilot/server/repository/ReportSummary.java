package io.github.atomoty.faultpilot.server.repository;

import java.time.Instant;

/**
 * Lightweight view of a stored diagnosis report for history listings — enough to render a row and
 * link to the full report, without the full timeline/evidence payload.
 */
public record ReportSummary(
        String diagnosisId,
        String projectId,
        String environment,
        Instant createdAt,
        String summary,
        boolean ruleFallback
) {
}
