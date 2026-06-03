package io.github.atomoty.faultpilot.server;

import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.server.repository.InMemoryEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies secrets are redacted at write time, so raw values never rest in the event store
 * (review #2). Reads the store directly rather than relying on read-side scrubbing.
 */
@SpringBootTest(properties = "faultpilot.ingestion.require-token=false")
@AutoConfigureMockMvc
class EventStoreRedactionIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    InMemoryEventStore eventStore;

    @Test
    void secretsAreRedactedBeforeStorage() throws Exception {
        Instant at = Instant.parse("2026-06-01T02:00:00Z");
        String body = """
                {"projectId":"order-service","environment":"local","type":"CONFIG_CHANGE",
                 "occurredAt":"%s","attributes":{"password":"plain-secret","key":"timeout"}}
                """.formatted(at);

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        List<ChangeEvent> stored = eventStore.query(new EvidenceQuery(
                "order-service", "local", at.minusSeconds(60), at.plusSeconds(60), 100));

        assertThat(stored).anySatisfy(e -> {
            assertThat(e.attributes().get("password")).isEqualTo("***");
            assertThat(e.attributes().get("key")).isEqualTo("timeout");
        });
    }
}
