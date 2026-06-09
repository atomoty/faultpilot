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

    private JdbcDiagnosisRepository repository;

    @BeforeEach
    void setUp() {
        var db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("diagrepo-" + java.util.UUID.randomUUID())
                .addScript("schema.sql")
                .build();
        repository = new JdbcDiagnosisRepository(new JdbcTemplate(db),
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
