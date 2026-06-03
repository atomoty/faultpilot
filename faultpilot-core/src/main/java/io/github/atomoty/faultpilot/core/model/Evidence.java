package io.github.atomoty.faultpilot.core.model;

/**
 * A flattened evidence reference embedded in the report so each id resolves to a description.
 * See design.md §11 response shape ("evidence": []).
 */
public record Evidence(
        String evidenceId,
        String type,
        String description
) {
}
