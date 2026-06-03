package io.github.atomoty.faultpilot.core.jdbc;

/**
 * Thrown when a database source cannot be queried at all (connection refused, auth failure, timeout).
 * It signals genuine unavailability so the collector marks the source unavailable rather than
 * reporting misleading empty evidence (spec §9). A missing optional sub-query (e.g. a system view
 * the account cannot read) is handled by skipping that sub-query, NOT by throwing this.
 */
public class DataSourceUnavailableException extends RuntimeException {
    public DataSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
