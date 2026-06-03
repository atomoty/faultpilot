package io.github.atomoty.faultpilot.core.jdbc;

import io.github.atomoty.faultpilot.core.log.LogSourceUnavailableException;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcLogReaderTest {

    private final JdbcLogReader reader = new JdbcLogReader();
    private String url;
    private Connection keepAlive; // keep the in-memory DB alive for the test duration

    @BeforeEach
    void setUp() throws SQLException {
        // Unique in-memory DB per test; DB_CLOSE_DELAY=-1 keeps it until the last connection closes.
        url = "jdbc:h2:mem:logdb_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1";
        keepAlive = DriverManager.getConnection(url);
        try (Statement st = keepAlive.createStatement()) {
            st.execute("""
                    CREATE TABLE app_log (
                        occurred_at TIMESTAMP,
                        log_level   VARCHAR(10),
                        trace_id    VARCHAR(64),
                        message     VARCHAR(1000),
                        stack_trace VARCHAR(4000)
                    )
                    """);
            st.execute("""
                    CREATE VIEW faultpilot_log_view AS
                    SELECT occurred_at, log_level AS level, trace_id, message, stack_trace FROM app_log
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        keepAlive.close();
    }

    private void insert(String ts, String level, String traceId, String msg, String stack) throws SQLException {
        try (var ps = keepAlive.prepareStatement(
                "INSERT INTO app_log(occurred_at, log_level, trace_id, message, stack_trace) VALUES (?,?,?,?,?)")) {
            ps.setString(1, ts);
            ps.setString(2, level);
            ps.setString(3, traceId);
            ps.setString(4, msg);
            ps.setString(5, stack);
            ps.executeUpdate();
        }
    }

    private JdbcLogSource source(String view) {
        return new JdbcLogSource(url, null, null, view, 2000, 3000, ZoneOffset.UTC);
    }

    private EvidenceQuery query(Instant from, Instant to, int max) {
        return new EvidenceQuery("p", "local", from, to, max);
    }

    @Test
    void readsAndNormalizesRows() throws SQLException {
        insert("2026-06-01 10:05:31.000", "ERROR", "t-1", "Create order failed",
                "java.lang.NullPointerException: x\n\tat com.example.OrderService.create(OrderService.java:88)");

        List<LogEvent> events = reader.read(source("faultpilot_log_view"),
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500));

        assertThat(events).hasSize(1);
        LogEvent e = events.get(0);
        assertThat(e.level()).isEqualTo("ERROR");
        assertThat(e.traceId()).isEqualTo("t-1");
        assertThat(e.attributes()).containsEntry("exceptionClass", "NullPointerException");
        assertThat(e.occurredAt()).isEqualTo(Instant.parse("2026-06-01T10:05:31Z"));
    }

    @Test
    void filtersByTimeRange() throws SQLException {
        insert("2026-06-01 08:00:00.000", "ERROR", "t", "early", null);
        insert("2026-06-01 10:00:00.000", "ERROR", "t", "in-window", null);

        List<LogEvent> events = reader.read(source("faultpilot_log_view"),
                query(Instant.parse("2026-06-01T09:00:00Z"), Instant.parse("2026-06-01T11:00:00Z"), 500));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).message()).isEqualTo("in-window");
    }

    @Test
    void dropsInfoAndDebugKeepsWarnAndError() throws SQLException {
        insert("2026-06-01 10:00:00.000", "DEBUG", "t", "debug", null);
        insert("2026-06-01 10:00:01.000", "INFO", "t", "info", null);
        insert("2026-06-01 10:00:02.000", "WARN", "t", "warn", null);
        insert("2026-06-01 10:00:03.000", "ERROR", "t", "error", null);

        List<LogEvent> events = reader.read(source("faultpilot_log_view"),
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500));

        assertThat(events).extracting(LogEvent::level).containsExactly("ERROR", "WARN");
    }

    @Test
    void capsByMaxResults() throws SQLException {
        for (int i = 0; i < 10; i++) {
            insert(String.format("2026-06-01 10:00:0%d.000", i), "ERROR", "t", "e" + i, null);
        }
        List<LogEvent> events = reader.read(source("faultpilot_log_view"),
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 3));

        assertThat(events).hasSize(3);
    }

    @Test
    void rejectsViewNameInjection() {
        assertThatThrownBy(() -> reader.read(source("faultpilot_log_view; DROP TABLE app_log"),
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> reader.read(source("app_log WHERE 1=1"),
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500)))
                .isInstanceOf(IllegalArgumentException.class);

        // table must still exist (injection never executed)
        assertThat(tableExists()).isTrue();
    }

    @Test
    void allowsSchemaQualifiedView() throws SQLException {
        // PUBLIC is H2's default schema; schema.view form must pass validation and resolve.
        insert("2026-06-01 10:00:00.000", "WARN", "t", "ok", null);
        List<LogEvent> events = reader.read(source("PUBLIC.faultpilot_log_view"),
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500));
        assertThat(events).hasSize(1);
    }

    @Test
    void connectionFailureThrowsUnavailable() {
        JdbcLogSource bad = new JdbcLogSource(
                "jdbc:h2:mem:nonexistent;IFEXISTS=TRUE", null, null,
                "faultpilot_log_view", 2000, 3000, ZoneOffset.UTC);

        assertThatThrownBy(() -> reader.read(bad,
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500)))
                .isInstanceOf(LogSourceUnavailableException.class);
    }

    private boolean tableExists() {
        try (Statement st = keepAlive.createStatement()) {
            st.executeQuery("SELECT COUNT(*) FROM app_log");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
