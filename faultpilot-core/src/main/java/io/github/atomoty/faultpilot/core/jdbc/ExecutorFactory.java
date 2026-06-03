package io.github.atomoty.faultpilot.core.jdbc;

/**
 * Opens a {@link SqlExecutor} for a query. Production uses {@link DataSourceConfig#openExecutor()};
 * tests supply a fake so dialect sources are exercised without a real database.
 *
 * @throws DataSourceUnavailableException if a connection cannot be opened
 */
@FunctionalInterface
public interface ExecutorFactory {
    SqlExecutor open();
}
