package io.github.atomoty.faultpilot.server.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.atomoty.faultpilot.core.model.DiagnosisReport;
import io.github.atomoty.faultpilot.core.model.Evidence;
import io.github.atomoty.faultpilot.core.model.EvidenceStrength;
import io.github.atomoty.faultpilot.core.model.RootCauseCandidate;
import io.github.atomoty.faultpilot.core.model.TimelineEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDiagnosisRepositoryTest {

    private JdbcTemplate jdbc;
    private JdbcDiagnosisRepository repository;

    @BeforeEach
    void setUp() {
        var db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("diagrepo-" + java.util.UUID.randomUUID())
                .addScript("schema.sql")
                .build();
        jdbc = new JdbcTemplate(db);
        repository = new JdbcDiagnosisRepository(jdbc,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void savesAndFindsReportWithNestedFields() {
        DiagnosisReport report = sampleReport("diag-1");

        repository.save(report);
        Optional<DiagnosisReport> found = repository.find("diag-1");

        assertThat(found).isPresent();
        DiagnosisReport r = found.get();
        assertThat(r.diagnosisId()).isEqualTo("diag-1");
        assertThat(r.projectId()).isEqualTo("order-service");
        assertThat(r.summary()).isEqualTo("pool contention");
        assertThat(r.timeline()).singleElement()
                .satisfies(t -> assertThat(t.evidenceId()).isEqualTo("log-1"));
        assertThat(r.rootCauseCandidates()).singleElement()
                .satisfies(c -> {
                    assertThat(c.label()).isEqualTo("slow-sql-pool-contention");
                    assertThat(c.strength()).isEqualTo(EvidenceStrength.STRONG);
                    assertThat(c.evidenceIds()).containsExactly("sql-1");
                });
        assertThat(r.evidence()).singleElement()
                .satisfies(e -> assertThat(e.evidenceId()).isEqualTo("sql-1"));
        assertThat(r.createdAt()).isEqualTo(Instant.parse("2026-06-01T03:00:00Z"));
    }

    @Test
    void findReturnsEmptyWhenAbsent() {
        assertThat(repository.find("missing")).isEmpty();
    }

    @Test
    void saveIsIdempotentOnSameId() {
        repository.save(sampleReport("diag-2"));
        repository.save(sampleReport("diag-2")); // must not throw on duplicate key
        assertThat(repository.find("diag-2")).isPresent();
    }

    @Test
    void listReturnsSummariesNewestFirstWithFields() {
        repository.save(report("a", "order-service", "local", "older", Instant.parse("2026-06-01T01:00:00Z")));
        repository.save(report("b", "order-service", "local", "newer", Instant.parse("2026-06-01T02:00:00Z")));

        List<ReportSummary> list = repository.list(null, null, 10);

        assertThat(list).extracting(ReportSummary::diagnosisId).containsExactly("b", "a");
        assertThat(list.get(0).summary()).isEqualTo("newer");
        assertThat(list.get(0).projectId()).isEqualTo("order-service");
        assertThat(list.get(0).environment()).isEqualTo("local");
        assertThat(list.get(0).createdAt()).isEqualTo(Instant.parse("2026-06-01T02:00:00Z"));
        assertThat(list.get(0).ruleFallback()).isFalse();
    }

    @Test
    void listFiltersByProjectAndEnvironment() {
        Instant at = Instant.parse("2026-06-01T01:00:00Z");
        repository.save(report("a", "order-service", "local", "keep", at));
        repository.save(report("b", "order-service", "staging", "wrong-env", at));
        repository.save(report("c", "other", "local", "wrong-project", at));

        List<ReportSummary> list = repository.list("order-service", "local", 10);

        assertThat(list).extracting(ReportSummary::diagnosisId).containsExactly("a");
    }

    @Test
    void listRespectsLimit() {
        Instant base = Instant.parse("2026-06-01T01:00:00Z");
        for (int i = 0; i < 5; i++) {
            repository.save(report("d" + i, "order-service", "local", "s" + i, base.plusSeconds(i)));
        }

        assertThat(repository.list(null, null, 2)).hasSize(2);
    }

    @Test
    void listSkipsCorruptRowInsteadOfFailing() {
        repository.save(report("good", "order-service", "local", "fine", Instant.parse("2026-06-01T01:00:00Z")));
        jdbc.update("INSERT INTO diagnosis_report (id, project_id, environment, created_at, report_json)"
                        + " VALUES (?, ?, ?, ?, ?)",
                "corrupt", "order-service", "local",
                java.sql.Timestamp.from(Instant.parse("2026-06-01T02:00:00Z")), "{not valid json");

        List<ReportSummary> list = repository.list(null, null, 10);

        assertThat(list).extracting(ReportSummary::diagnosisId).containsExactly("good");
    }

    private DiagnosisReport report(String id, String projectId, String environment, String summary, Instant createdAt) {
        return new DiagnosisReport(id, projectId, environment, summary,
                List.of(), List.of(), List.of(), List.of(), List.of(), null, false,
                DiagnosisReport.DISCLAIMER, createdAt);
    }

    private DiagnosisReport sampleReport(String id) {
        return new DiagnosisReport(
                id, "order-service", "local", "pool contention",
                List.of(new TimelineEntry(Instant.parse("2026-06-01T02:30:00Z"), "SLOW_SQL", "slow", "log-1")),
                List.of(new RootCauseCandidate("slow-sql-pool-contention", "Pool contention",
                        "slow query holds the pool", EvidenceStrength.STRONG, List.of("sql-1"))),
                List.of("Run EXPLAIN"),
                List.of(new Evidence("sql-1", "SLOW_SQL", "avg=2100ms")),
                List.of(), null, false, DiagnosisReport.DISCLAIMER,
                Instant.parse("2026-06-01T03:00:00Z"));
    }
}
