package io.github.atomoty.faultpilot.core.jdbc.db;

import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DbSlowSqlSourceTest {

    private EvidenceQuery query(int max) {
        return new EvidenceQuery("p", "local",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), max);
    }

    private Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void mysqlMapsDigestRowsToSummaries() {
        FakeSqlExecutor fake = new FakeSqlExecutor().stub("events_statements_summary_by_digest", List.of(
                row("DIGEST_TEXT", "SELECT * FROM orders WHERE id = ?", "COUNT_STAR", 320,
                        "avg_ms", 2100, "max_ms", 2850)));
        MysqlSlowSqlSource source = new MysqlSlowSqlSource(() -> fake);

        List<SlowSqlSummary> out = source.query(query(500));

        assertThat(out).hasSize(1);
        SlowSqlSummary s = out.get(0);
        assertThat(s.sqlTemplate()).isEqualTo("SELECT * FROM orders WHERE id = ?");
        assertThat(s.occurrences()).isEqualTo(320);
        assertThat(s.avgDurationMs()).isEqualTo(2100);
        assertThat(s.maxDurationMs()).isEqualTo(2850);
        assertThat(s.source()).isEqualTo("mysql-digest");
    }

    @Test
    void mysqlMissingDigestTableSkipsWithoutThrowing() {
        FakeSqlExecutor fake = new FakeSqlExecutor().stubFailure("events_statements_summary_by_digest");
        MysqlSlowSqlSource source = new MysqlSlowSqlSource(() -> fake);

        assertThat(source.query(query(500))).isEmpty();
    }

    @Test
    void postgresMapsStatementsToSummaries() {
        FakeSqlExecutor fake = new FakeSqlExecutor().stub("pg_stat_statements", List.of(
                row("query", "select * from orders where id = $1", "calls", 12,
                        "mean_exec_time", 41, "max_exec_time", 90)));
        PostgresSlowSqlSource source = new PostgresSlowSqlSource(() -> fake);

        List<SlowSqlSummary> out = source.query(query(500));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).sqlTemplate()).isEqualTo("select * from orders where id = $1");
        assertThat(out.get(0).occurrences()).isEqualTo(12);
        assertThat(out.get(0).source()).isEqualTo("pg_stat_statements");
    }

    @Test
    void postgresExtensionMissingSkipsWithoutThrowing() {
        FakeSqlExecutor fake = new FakeSqlExecutor().stubFailure("pg_stat_statements");
        PostgresSlowSqlSource source = new PostgresSlowSqlSource(() -> fake);

        assertThat(source.query(query(500))).isEmpty();
    }

    @Test
    void respectsMaxResults() {
        FakeSqlExecutor fake = new FakeSqlExecutor().stub("events_statements_summary_by_digest", List.of(
                row("DIGEST_TEXT", "a", "COUNT_STAR", 1, "avg_ms", 30, "max_ms", 40),
                row("DIGEST_TEXT", "b", "COUNT_STAR", 1, "avg_ms", 20, "max_ms", 25),
                row("DIGEST_TEXT", "c", "COUNT_STAR", 1, "avg_ms", 10, "max_ms", 15)));
        MysqlSlowSqlSource source = new MysqlSlowSqlSource(() -> fake);

        assertThat(source.query(query(2))).hasSize(2);
    }
}
