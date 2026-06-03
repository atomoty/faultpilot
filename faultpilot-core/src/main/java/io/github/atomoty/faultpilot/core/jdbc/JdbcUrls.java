package io.github.atomoty.faultpilot.core.jdbc;

/**
 * Helpers for logging JDBC URLs without leaking credentials. A JDBC URL can carry secrets in its
 * query string (e.g. {@code ?password=...}, cloud auth tokens, signatures), so only the part before
 * the first {@code ?} is safe to log (review P2).
 */
public final class JdbcUrls {

    private JdbcUrls() {
    }

    /** Return the URL with any query string and userinfo removed, safe for logs/exceptions. */
    public static String safe(String url) {
        if (url == null) {
            return "(none)";
        }
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }
}
