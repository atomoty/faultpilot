package io.github.atomoty.faultpilot.core.jdbc.db;

import io.github.atomoty.faultpilot.core.adapter.DatabaseHealthSourceAdapter;
import io.github.atomoty.faultpilot.core.jdbc.DataSourceConfig;
import io.github.atomoty.faultpilot.core.jdbc.DataSourceUnavailableException;
import io.github.atomoty.faultpilot.core.jdbc.ExecutorFactory;
import io.github.atomoty.faultpilot.core.jdbc.SqlExecutor;
import io.github.atomoty.faultpilot.core.model.DatabaseHealthSnapshot;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.sanitize.EvidenceSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a read-only PostgreSQL health snapshot (design §9.2): {@code pg_stat_activity} for connection
 * states and long transactions, {@code pg_locks} for lock waits (skipped if unreadable).
 *
 * <p>A connection failure propagates as {@link DataSourceUnavailableException}; an individual
 * sub-query failure is skipped, leaving the snapshot available.
 */
public class PostgresDatabaseHealthSource implements DatabaseHealthSourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(PostgresDatabaseHealthSource.class);

    private final ExecutorFactory executorFactory;
    private final DataSourceConfig config;
    private final EvidenceSanitizer sanitizer;

    public PostgresDatabaseHealthSource(ExecutorFactory executorFactory, DataSourceConfig config,
                                        EvidenceSanitizer sanitizer) {
        this.executorFactory = executorFactory;
        this.config = config;
        this.sanitizer = sanitizer;
    }

    @Override
    public DatabaseHealthSnapshot query(EvidenceQuery query) {
        try (SqlExecutor exec = executorFactory.open()) {
            int active = countActivity(exec, "state = 'active'");
            int idle = countActivity(exec, "state = 'idle'");
            int waiting = countActivity(exec, "wait_event_type IS NOT NULL AND state = 'active'");

            List<String> longTx = longTransactions(exec);
            List<String> lockWaits = lockWaits(exec);

            return new DatabaseHealthSnapshot("dbhealth-1", true, null,
                    active, idle, waiting, longTx, lockWaits);
        } catch (DataSourceUnavailableException unavailable) {
            log.warn("PostgreSQL health source unavailable for project {}: {} (cause: {})",
                    query.projectId(), unavailable.getMessage(), unavailable.rootCauseSummary());
            return DatabaseHealthSnapshot.unavailable("PostgreSQL unavailable");
        }
    }

    private int countActivity(SqlExecutor exec, String where) {
        try {
            List<Long> n = exec.query(
                    "SELECT count(*) AS c FROM pg_stat_activity WHERE " + where,
                    rs -> rs.getLong("c"));
            return n.isEmpty() ? 0 : n.get(0).intValue();
        } catch (SQLException e) {
            log.warn("pg_stat_activity count failed ({}), treating as 0: {}", where, e.toString());
            return 0;
        }
    }

    private List<String> longTransactions(SqlExecutor exec) {
        String sql = "SELECT pid, query, now() - xact_start AS age FROM pg_stat_activity"
                + " WHERE xact_start IS NOT NULL"
                + " AND now() - xact_start > interval '" + config.longTxThreshold().toSeconds() + " seconds'";
        try {
            List<String> out = new ArrayList<>();
            exec.query(sql, rs -> out.add(sanitizer.redact(
                    "pid=" + rs.getString("pid") + " age=" + rs.getString("age")
                            + " query=" + rs.getString("query"))));
            return out;
        } catch (SQLException e) {
            log.warn("pg_stat_activity long-tx query unavailable, skipping: {}", e.toString());
            return List.of();
        }
    }

    private List<String> lockWaits(SqlExecutor exec) {
        try {
            List<String> out = new ArrayList<>();
            exec.query("SELECT pid, locktype, mode FROM pg_locks WHERE NOT granted",
                    rs -> out.add(sanitizer.redact("pid=" + rs.getString("pid")
                            + " locktype=" + rs.getString("locktype") + " mode=" + rs.getString("mode"))));
            return out;
        } catch (SQLException e) {
            log.warn("pg_locks unavailable, skipping lock waits: {}", e.toString());
            return List.of();
        }
    }
}
