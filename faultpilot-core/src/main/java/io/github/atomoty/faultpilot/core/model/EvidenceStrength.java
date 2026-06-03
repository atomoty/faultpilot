package io.github.atomoty.faultpilot.core.model;

/**
 * Strength of a root-cause candidate, computed by deterministic rules (not by the model).
 * See specification.md §10.
 */
public enum EvidenceStrength {
    /** At least two independent evidence sources plus a traceId or explicit rule hit. */
    STRONG,
    /** At least two independent evidence sources, related only by time window or module. */
    MODERATE,
    /** A single evidence source, or insufficient evidence. */
    WEAK
}
