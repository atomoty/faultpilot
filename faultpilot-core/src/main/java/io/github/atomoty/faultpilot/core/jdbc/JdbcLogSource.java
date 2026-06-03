package io.github.atomoty.faultpilot.core.jdbc;

import java.time.ZoneId;

/**
 * Resolved configuration for reading a project's logs from a database table/view. Built by the
 * server from the project's {@code logs} block; kept Spring-free so the reader stays unit-testable.
 *
 * <p>The {@code view} must expose the canonical columns
 * {@code occurred_at, level, trace_id, message, stack_trace} (design §4.4) — projects alias their
 * own table with a read-only view. {@code view} is the only project-controlled identifier and is
 * validated before use to prevent SQL injection.
 *
 * @param zone zone used to interpret {@code occurred_at} values that carry no offset (spec §7.1)
 */
public record JdbcLogSource(
        String url,
        String username,
        String password,
        String view,
        int connectTimeoutMs,
        int queryTimeoutMs,
        ZoneId zone
) {
    public JdbcLogSource {
        zone = zone == null ? ZoneId.systemDefault() : zone;
    }
}
