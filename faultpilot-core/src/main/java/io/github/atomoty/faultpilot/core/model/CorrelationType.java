package io.github.atomoty.faultpilot.core.model;

/**
 * How two pieces of evidence are correlated. See specification.md §7.2.
 */
public enum CorrelationType {
    /** Linked by a shared traceId. */
    TRACE_ID,
    /** Linked by request URI, SQL source and time window. */
    REQUEST_SCOPED,
    /** Linked by service/module, exception class and time window. */
    MODULE_SCOPED,
    /** Linked only by time proximity; must not be stated as definite causation. */
    TEMPORAL_ONLY
}
