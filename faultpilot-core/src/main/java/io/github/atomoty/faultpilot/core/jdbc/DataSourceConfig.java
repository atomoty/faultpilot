package io.github.atomoty.faultpilot.core.jdbc;

import java.time.Duration;

/**
 * Resolved connection configuration for read-only database analysis (health + slow SQL). Built by
 * the server from the project's {@code database} block; kept Spring-free so the dialect sources stay
 * unit-testable. {@code longTxThreshold} flags transactions running longer than this as "long".
 */
public record DataSourceConfig(
        String url,
        String username,
        String password,
        int connectTimeoutMs,
        int queryTimeoutMs,
        Duration longTxThreshold
) {
    public DataSourceConfig {
        longTxThreshold = longTxThreshold == null ? Duration.ofSeconds(30) : longTxThreshold;
    }

    public JdbcSqlExecutor openExecutor() {
        return JdbcSqlExecutor.open(url, username, password, connectTimeoutMs, queryTimeoutMs);
    }
}
