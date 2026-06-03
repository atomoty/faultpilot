package io.github.atomoty.faultpilot.core.jdbc.db;

import io.github.atomoty.faultpilot.core.adapter.SlowSqlSourceAdapter;
import io.github.atomoty.faultpilot.core.jdbc.ExecutorFactory;
import io.github.atomoty.faultpilot.core.jdbc.SqlExecutor;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Reads slow-SQL summaries from PostgreSQL's {@code pg_stat_statements} (design §9.2). The normalized
 * {@code query} text already replaces literals with placeholders. {@code mean/max_exec_time} are
 * milliseconds.
 *
 * <p>If the {@code pg_stat_statements} extension is not installed (or the account cannot read it) the
 * query raises a {@link SQLException}; the source skips and returns empty rather than failing the
 * diagnosis. A connection failure propagates as
 * {@link io.github.atomoty.faultpilot.core.jdbc.DataSourceUnavailableException}.
 */
public class PostgresSlowSqlSource implements SlowSqlSourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(PostgresSlowSqlSource.class);

    private static final String SQL = """
            SELECT query, calls, mean_exec_time, max_exec_time
            FROM pg_stat_statements
            WHERE query IS NOT NULL
            ORDER BY mean_exec_time DESC
            """;

    private final ExecutorFactory executorFactory;

    public PostgresSlowSqlSource(ExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    @Override
    public List<SlowSqlSummary> query(EvidenceQuery query) {
        try (SqlExecutor exec = executorFactory.open()) {
            int[] idx = {0};
            return exec.query(SQL, query.maxResults(), rs -> new SlowSqlSummary(
                    "dbsql-" + (++idx[0]),
                    rs.getString("query"),
                    rs.getLong("calls"),
                    Math.round(rs.getDouble("mean_exec_time")),
                    Math.round(rs.getDouble("max_exec_time")),
                    query.from(),
                    "pg_stat_statements"));
        } catch (SQLException e) {
            log.warn("pg_stat_statements unavailable for project {} (extension not enabled?), skipping: {}",
                    query.projectId(), e.toString());
            return List.of();
        }
    }
}
