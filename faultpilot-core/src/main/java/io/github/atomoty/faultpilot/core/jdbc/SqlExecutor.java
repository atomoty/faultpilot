package io.github.atomoty.faultpilot.core.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Minimal read-only query runner. It exists to separate "connect and run SQL" from the dialect
 * mapping logic, so the latter is unit-testable with a fake executor (no real database).
 *
 * <p>Extends {@link AutoCloseable} with a non-throwing {@link #close()} so callers can use
 * try-with-resources and fakes can no-op.
 */
public interface SqlExecutor extends AutoCloseable {

    /** Maps one {@link ResultSet} row to a value. */
    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * Run a read-only query and map all rows.
     *
     * @throws DataSourceUnavailableException if the database itself is unreachable/unauthorized
     * @throws SQLException                   for a query-level error the caller may choose to tolerate
     *                                        (e.g. a missing system view / insufficient privilege)
     */
    <T> List<T> query(String sql, RowMapper<T> mapper) throws SQLException;

    /**
     * Run a read-only query capped to at most {@code maxRows} rows at the JDBC layer (so a large
     * system view is not fully pulled into memory). Default ignores the cap; the JDBC implementation
     * applies it via {@code setMaxRows}.
     */
    default <T> List<T> query(String sql, int maxRows, RowMapper<T> mapper) throws SQLException {
        return query(sql, mapper);
    }

    /** Release the underlying connection. Overrides {@link AutoCloseable#close()} to not throw. */
    @Override
    void close();
}
