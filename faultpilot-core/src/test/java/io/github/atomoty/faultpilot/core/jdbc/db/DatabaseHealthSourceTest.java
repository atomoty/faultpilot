package io.github.atomoty.faultpilot.core.jdbc.db;

import io.github.atomoty.faultpilot.core.jdbc.DataSourceConfig;
import io.github.atomoty.faultpilot.core.jdbc.DataSourceUnavailableException;
import io.github.atomoty.faultpilot.core.model.DatabaseHealthSnapshot;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.sanitize.EvidenceSanitizer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseHealthSourceTest {

    private final EvidenceSanitizer sanitizer = new EvidenceSanitizer();
    private final DataSourceConfig config = new DataSourceConfig(
            "jdbc:fake", "sa", "", 2000, 3000, Duration.ofSeconds(30));

    private EvidenceQuery query() {
        return new EvidenceQuery("p", "local",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500);
    }

    private Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void mysqlMapsStatusTxAndLockWaits() {
        FakeSqlExecutor fake = new FakeSqlExecutor()
                .stub("SHOW GLOBAL STATUS", List.of(
                        row("Variable_name", "Threads_connected", "Value", "20"),
                        row("Variable_name", "Threads_running", "Value", "5")))
                .stub("innodb_trx", List.of(
                        row("trx_id", "T1", "trx_started", "2026-06-01 10:00:00", "trx_query", "UPDATE x")))
                .stub("data_lock_waits", List.of(
                        row("BLOCKING_ENGINE_TRANSACTION_ID", "T2", "REQUESTING_ENGINE_TRANSACTION_ID", "T1")));

        DatabaseHealthSnapshot snap = new MysqlDatabaseHealthSource(() -> fake, config, sanitizer).query(query());

        assertThat(snap.available()).isTrue();
        assertThat(snap.activeConnections()).isEqualTo(5);
        assertThat(snap.idleConnections()).isEqualTo(15); // 20 - 5
        assertThat(snap.longTransactions()).hasSize(1);
        assertThat(snap.lockWaits()).hasSize(1);
    }

    @Test
    void mysqlLockWaitPrivilegeFailureSkippedButSnapshotStillAvailable() {
        FakeSqlExecutor fake = new FakeSqlExecutor()
                .stub("SHOW GLOBAL STATUS", List.of(
                        row("Variable_name", "Threads_connected", "Value", "3"),
                        row("Variable_name", "Threads_running", "Value", "1")))
                .stub("innodb_trx", List.of())
                .stubFailure("data_lock_waits"); // no privilege

        DatabaseHealthSnapshot snap = new MysqlDatabaseHealthSource(() -> fake, config, sanitizer).query(query());

        assertThat(snap.available()).isTrue();        // partial degrade, not failure
        assertThat(snap.activeConnections()).isEqualTo(1);
        assertThat(snap.lockWaits()).isEmpty();
    }

    @Test
    void mysqlConnectionFailureYieldsUnavailableSnapshot() {
        DatabaseHealthSnapshot snap = new MysqlDatabaseHealthSource(
                () -> { throw new DataSourceUnavailableException("down", null); }, config, sanitizer).query(query());

        assertThat(snap.available()).isFalse();
        assertThat(snap.unavailableReason()).isNotBlank();
    }

    @Test
    void postgresMapsActivityAndLocks() {
        // Register the waiting stub first: its query also contains "state = 'active'", so the more
        // specific substring must be matched before the plain active-count stub.
        FakeSqlExecutor fake = new FakeSqlExecutor()
                .stub("wait_event_type IS NOT NULL", List.of(row("c", "2")))
                .stub("state = 'active'", List.of(row("c", "4")))
                .stub("state = 'idle'", List.of(row("c", "10")))
                .stub("xact_start IS NOT NULL", List.of(
                        row("pid", "123", "query", "SELECT 1", "age", "00:05:00")))
                .stub("pg_locks", List.of(row("pid", "123", "locktype", "relation", "mode", "AccessShareLock")));

        DatabaseHealthSnapshot snap = new PostgresDatabaseHealthSource(() -> fake, config, sanitizer).query(query());

        assertThat(snap.available()).isTrue();
        assertThat(snap.activeConnections()).isEqualTo(4);
        assertThat(snap.idleConnections()).isEqualTo(10);
        assertThat(snap.waitingConnections()).isEqualTo(2);
        assertThat(snap.longTransactions()).hasSize(1);
        assertThat(snap.lockWaits()).hasSize(1);
    }

    @Test
    void postgresConnectionFailureYieldsUnavailableSnapshot() {
        DatabaseHealthSnapshot snap = new PostgresDatabaseHealthSource(
                () -> { throw new DataSourceUnavailableException("down", null); }, config, sanitizer).query(query());

        assertThat(snap.available()).isFalse();
    }
}
