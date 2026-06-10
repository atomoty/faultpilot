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

    /**
     * The underlying failure as {@code SimpleClassName: message}, walking to the deepest cause
     * (e.g. {@code CommunicationsException: Communications link failure} or
     * {@code SQLException: Access denied}). Returns an empty string when there is no cause, so it
     * can be logged unconditionally. Lets a single WARN line carry the actual reason a connection
     * failed instead of only the (sanitized) JDBC URL.
     */
    public String rootCauseSummary() {
        Throwable root = getCause();
        if (root == null) {
            return "";
        }
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
