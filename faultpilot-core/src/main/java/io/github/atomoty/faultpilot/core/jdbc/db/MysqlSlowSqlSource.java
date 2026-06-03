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
 * Reads slow-SQL summaries from MySQL's {@code performance_schema.events_statements_summary_by_digest}
 * (design §9.1). Only the normalized {@code DIGEST_TEXT} template is carried — never real parameters.
 * Timer columns are picoseconds; converted to milliseconds.
 *
 * <p>If the digest table is unreadable (insufficient privilege / performance_schema disabled) the
 * source returns an empty list rather than failing the diagnosis. A connection failure propagates as
 * {@link io.github.atomoty.faultpilot.core.jdbc.DataSourceUnavailableException}.
 */
public class MysqlSlowSqlSource implements SlowSqlSourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(MysqlSlowSqlSource.class);

    private static final String SQL = """
            SELECT DIGEST_TEXT, COUNT_STAR,
                   AVG_TIMER_WAIT/1000000000 AS avg_ms,
                   MAX_TIMER_WAIT/1000000000 AS max_ms
            FROM performance_schema.events_statements_summary_by_digest
            WHERE DIGEST_TEXT IS NOT NULL
            ORDER BY AVG_TIMER_WAIT DESC
            """;

    private final ExecutorFactory executorFactory;

    public MysqlSlowSqlSource(ExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    @Override
    public List<SlowSqlSummary> query(EvidenceQuery query) {
        try (SqlExecutor exec = executorFactory.open()) {
            int[] idx = {0};
            return exec.query(SQL, query.maxResults(), rs -> new SlowSqlSummary(
                    "dbsql-" + (++idx[0]),
                    rs.getString("DIGEST_TEXT"),
                    rs.getLong("COUNT_STAR"),
                    Math.round(rs.getDouble("avg_ms")),
                    Math.round(rs.getDouble("max_ms")),
                    query.from(),
                    "mysql-digest"));
        } catch (SQLException e) {
            log.warn("MySQL slow-SQL digest unavailable for project {}, skipping: {}",
                    query.projectId(), e.toString());
            return List.of();
        }
    }
}
