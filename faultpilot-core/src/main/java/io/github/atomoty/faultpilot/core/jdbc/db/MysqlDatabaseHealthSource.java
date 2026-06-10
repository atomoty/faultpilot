package io.github.atomoty.faultpilot.core.jdbc.db;

import io.github.atomoty.faultpilot.core.adapter.DatabaseHealthSourceAdapter;
import io.github.atomoty.faultpilot.core.jdbc.DataSourceConfig;
import io.github.atomoty.faultpilot.core.jdbc.ExecutorFactory;
import io.github.atomoty.faultpilot.core.jdbc.SqlExecutor;
import io.github.atomoty.faultpilot.core.model.DatabaseHealthSnapshot;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.sanitize.EvidenceSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a read-only MySQL health snapshot (design §9.1): {@code SHOW GLOBAL STATUS} for connection
 * counts, {@code information_schema.innodb_trx} for long transactions, and
 * {@code performance_schema.data_lock_waits} for lock waits (skipped if the account cannot read it).
 *
 * <p>A connection failure propagates as
 * {@link io.github.atomoty.faultpilot.core.jdbc.DataSourceUnavailableException}; an individual
 * sub-query failure is skipped, leaving the snapshot available.
 */
public class MysqlDatabaseHealthSource implements DatabaseHealthSourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(MysqlDatabaseHealthSource.class);

    private final ExecutorFactory executorFactory;
    private final DataSourceConfig config;
    private final EvidenceSanitizer sanitizer;

    public MysqlDatabaseHealthSource(ExecutorFactory executorFactory, DataSourceConfig config,
                                     EvidenceSanitizer sanitizer) {
        this.executorFactory = executorFactory;
        this.config = config;
        this.sanitizer = sanitizer;
    }

    @Override
    public DatabaseHealthSnapshot query(EvidenceQuery query) {
        try (SqlExecutor exec = executorFactory.open()) {
            Map<String, Long> status = globalStatus(exec);
            long connected = status.getOrDefault("threads_connected", 0L);
            long running = status.getOrDefault("threads_running", 0L);
            int active = (int) running;
            int idle = (int) Math.max(0, connected - running);

            List<String> longTx = longTransactions(exec);
            List<String> lockWaits = lockWaits(exec);

            return new DatabaseHealthSnapshot("dbhealth-1", true, null,
                    active, idle, 0, longTx, lockWaits);
        } catch (io.github.atomoty.faultpilot.core.jdbc.DataSourceUnavailableException unavailable) {
            log.warn("MySQL health source unavailable for project {}: {} (cause: {})",
                    query.projectId(), unavailable.getMessage(), unavailable.rootCauseSummary());
            return DatabaseHealthSnapshot.unavailable("MySQL unavailable");
        }
    }

    private Map<String, Long> globalStatus(SqlExecutor exec) {
        Map<String, Long> map = new HashMap<>();
        try {
            exec.query("SHOW GLOBAL STATUS", rs -> {
                String name = rs.getString(1);
                String value = rs.getString(2);
                if (name != null && value != null && value.matches("\\d+")) {
                    map.put(name.toLowerCase(), Long.parseLong(value));
                }
                return null;
            });
        } catch (SQLException e) {
            log.warn("SHOW GLOBAL STATUS failed, connection counts unavailable: {}", e.toString());
        }
        return map;
    }

    private List<String> longTransactions(SqlExecutor exec) {
        String sql = "SELECT trx_id, trx_started, trx_query FROM information_schema.innodb_trx"
                + " WHERE trx_started < (NOW() - INTERVAL " + config.longTxThreshold().toSeconds() + " SECOND)";
        try {
            List<String> out = new ArrayList<>();
            exec.query(sql, rs -> {
                out.add(sanitizer.redact("trx=" + rs.getString("trx_id")
                        + " started=" + rs.getString("trx_started")
                        + " query=" + rs.getString("trx_query")));
                return null;
            });
            return out;
        } catch (SQLException e) {
            log.warn("innodb_trx unavailable, skipping long transactions: {}", e.toString());
            return List.of();
        }
    }

    private List<String> lockWaits(SqlExecutor exec) {
        try {
            List<String> out = new ArrayList<>();
            exec.query("SELECT * FROM performance_schema.data_lock_waits", rs ->
                    out.add(sanitizer.redact("blocking="
                            + rs.getString("BLOCKING_ENGINE_TRANSACTION_ID")
                            + " waiting=" + rs.getString("REQUESTING_ENGINE_TRANSACTION_ID"))));
            return out;
        } catch (SQLException e) {
            log.warn("data_lock_waits query failed, skipping lock waits: {}", e.toString());
            return List.of();
        }
    }
}
