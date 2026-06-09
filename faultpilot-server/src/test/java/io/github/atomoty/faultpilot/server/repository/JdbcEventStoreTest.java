package io.github.atomoty.faultpilot.server.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEventStoreTest {

    private JdbcEventStore store;

    @BeforeEach
    void setUp() {
        var db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("eventstore-" + java.util.UUID.randomUUID())
                .addScript("schema.sql")
                .build();
        store = new JdbcEventStore(new JdbcTemplate(db),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void saveAssignsEvidenceIdAndRoundTripsAttributes() {
        Instant at = Instant.parse("2026-06-01T02:00:00Z");
        ChangeEvent saved = store.save(new ChangeEvent(
                null, "order-service", "local", "CONFIG_CHANGE", at, Map.of("key", "timeout")));

        assertThat(saved.evidenceId()).startsWith("event-");

        List<ChangeEvent> found = store.query(new EvidenceQuery(
                "order-service", "local", at.minusSeconds(60), at.plusSeconds(60), 100));

        assertThat(found).singleElement().satisfies(e -> {
            assertThat(e.evidenceId()).isEqualTo(saved.evidenceId());
            assertThat(e.type()).isEqualTo("CONFIG_CHANGE");
            assertThat(e.occurredAt()).isEqualTo(at);
            assertThat(e.attributes()).containsEntry("key", "timeout");
        });
    }

    @Test
    void evidenceIdsAreUnique() {
        Instant at = Instant.parse("2026-06-01T02:00:00Z");
        ChangeEvent a = store.save(new ChangeEvent(null, "p", "local", "T", at, Map.of()));
        ChangeEvent b = store.save(new ChangeEvent(null, "p", "local", "T", at, Map.of()));
        assertThat(a.evidenceId()).isNotEqualTo(b.evidenceId());
    }

    @Test
    void queryFiltersByProjectEnvironmentAndTime() {
        Instant at = Instant.parse("2026-06-01T02:00:00Z");
        store.save(new ChangeEvent(null, "order-service", "local", "T", at, Map.of()));
        store.save(new ChangeEvent(null, "other", "local", "T", at, Map.of()));        // wrong project
        store.save(new ChangeEvent(null, "order-service", "staging", "T", at, Map.of())); // wrong env
        store.save(new ChangeEvent(null, "order-service", "local", "T",
                Instant.parse("2026-06-02T00:00:00Z"), Map.of()));                       // out of window

        List<ChangeEvent> found = store.query(new EvidenceQuery(
                "order-service", "local", at.minusSeconds(60), at.plusSeconds(60), 100));

        assertThat(found).hasSize(1);
    }

    @Test
    void queryKeepsOnlyRecentMaxResultsInTimelineOrder() {
        Instant base = Instant.parse("2026-06-01T02:00:00Z");
        store.save(new ChangeEvent(null, "order-service", "local", "T", base, Map.of("n", "1")));
        store.save(new ChangeEvent(null, "order-service", "local", "T", base.plusSeconds(60), Map.of("n", "2")));
        store.save(new ChangeEvent(null, "order-service", "local", "T", base.plusSeconds(120), Map.of("n", "3")));

        List<ChangeEvent> found = store.query(new EvidenceQuery(
                "order-service", "local", base.minusSeconds(60), base.plusSeconds(180), 2));

        assertThat(found).extracting(e -> e.attributes().get("n"))
                .containsExactly("2", "3");
        assertThat(found).extracting(ChangeEvent::occurredAt)
                .containsExactly(base.plusSeconds(60), base.plusSeconds(120));
    }
}
