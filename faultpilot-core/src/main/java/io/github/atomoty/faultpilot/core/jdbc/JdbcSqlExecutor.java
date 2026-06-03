package io.github.atomoty.faultpilot.core.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * A {@link SqlExecutor} backed by a single read-only JDBC connection held for the duration of one
 * snapshot. Opening the connection is where unavailability is detected — a failure there throws
 * {@link DataSourceUnavailableException}. Per-query failures (e.g. a system view the account cannot
 * read) surface as {@link SQLException} so callers can skip just that sub-query.
 */
public final class JdbcSqlExecutor implements SqlExecutor, AutoCloseable {

    private final Connection connection;
    private final int queryTimeoutSeconds;

    private JdbcSqlExecutor(Connection connection, int queryTimeoutSeconds) {
        this.connection = connection;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    /** Open a read-only connection; throws {@link DataSourceUnavailableException} if it cannot. */
    public static JdbcSqlExecutor open(String url, String username, String password,
                                       int connectTimeoutMs, int queryTimeoutMs) {
        Properties props = new Properties();
        if (username != null) {
            props.setProperty("user", username);
        }
        if (password != null) {
            props.setProperty("password", password);
        }
        DriverManager.setLoginTimeout(Math.max(1, connectTimeoutMs / 1000));
        try {
            Connection conn = DriverManager.getConnection(url, props);
            conn.setReadOnly(true);
            return new JdbcSqlExecutor(conn, Math.max(1, queryTimeoutMs / 1000));
        } catch (SQLException e) {
            throw new DataSourceUnavailableException("Database unavailable: " + JdbcUrls.safe(url), e);
        }
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper) throws SQLException {
        return query(sql, 0, mapper);
    }

    @Override
    public <T> List<T> query(String sql, int maxRows, RowMapper<T> mapper) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setQueryTimeout(queryTimeoutSeconds);
            if (maxRows > 0) {
                ps.setMaxRows(maxRows);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // best-effort close
        }
    }
}
