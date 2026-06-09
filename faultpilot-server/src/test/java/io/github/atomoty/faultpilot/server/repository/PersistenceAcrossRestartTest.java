package io.github.atomoty.faultpilot.server.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.DiagnosisReport;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves reports and events survive a process restart: write with one repository instance, then
 * open a brand-new instance against the same on-disk H2 file and read the data back.
 */
class PersistenceAcrossRestartTest {

    @Test
    void reportAndEventSurviveReopeningTheFileDatabase(@TempDir Path dir) {
        String url = "jdbc:h2:file:" + dir.resolve("store").toAbsolutePath() + ";DB_CLOSE_DELAY=-1";
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Instant at = Instant.parse("2026-06-01T02:00:00Z");

        // First "process": create schema, write a report and an event.
        DataSource ds1 = dataSource(url);
        initSchema(ds1);
        JdbcTemplate jdbc1 = new JdbcTemplate(ds1);
        new JdbcDiagnosisRepository(jdbc1, mapper).save(report("diag-1", at));
        new JdbcEventStore(jdbc1, mapper).save(new ChangeEvent(
                null, "order-service", "local", "CONFIG_CHANGE", at, Map.of("key", "timeout")));

        // Second "process": fresh datasource + repositories on the same file.
        DataSource ds2 = dataSource(url);
        JdbcTemplate jdbc2 = new JdbcTemplate(ds2);

        assertThat(new JdbcDiagnosisRepository(jdbc2, mapper).find("diag-1")).isPresent();
        List<ChangeEvent> events = new JdbcEventStore(jdbc2, mapper).query(new EvidenceQuery(
                "order-service", "local", at.minusSeconds(60), at.plusSeconds(60), 100));
        assertThat(events).singleElement()
                .satisfies(e -> assertThat(e.attributes()).containsEntry("key", "timeout"));
    }

    private DataSource dataSource(String url) {
        return new DriverManagerDataSource(url, "sa", "");
    }

    private void initSchema(DataSource ds) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        populator.execute(ds);
    }

    private DiagnosisReport report(String id, Instant createdAt) {
        return new DiagnosisReport(id, "order-service", "local", "summary",
                List.of(), List.of(), List.of(), List.of(), List.of(), null, false,
                DiagnosisReport.DISCLAIMER, createdAt);
    }
}
