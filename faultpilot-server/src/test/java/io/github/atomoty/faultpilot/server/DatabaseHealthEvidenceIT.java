package io.github.atomoty.faultpilot.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.core.adapter.DatabaseHealthSourceAdapter;
import io.github.atomoty.faultpilot.core.model.DatabaseHealthSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies a database health snapshot is threaded into the report as DB_HEALTH evidence + timeline.
 * Uses a stub {@link DatabaseHealthSourceAdapter} (no real database) to validate the wiring.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseHealthEvidenceIT {

    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        DatabaseHealthSourceAdapter stubDatabaseHealth() {
            return query -> new DatabaseHealthSnapshot("dbhealth-1", true, null,
                    7, 13, 2, List.of("trx=T1 age=00:05:00"), List.of("pid=42 mode=AccessExclusiveLock"));
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void databaseHealthSnapshotBecomesEvidenceAndTimeline() throws Exception {
        // order-service/local exists in application.yml; the stub supplies a health snapshot for it.
        String body = """
                {"projectId":"order-service","environment":"local","question":"db state?",
                 "from":"2026-06-01T01:00:00Z","to":"2026-06-01T03:00:00Z"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode report = objectMapper.readTree(result.getResponse().getContentAsString());

        JsonNode dbEvidence = null;
        for (JsonNode e : report.get("evidence")) {
            if ("DB_HEALTH".equals(e.get("type").asText())) {
                dbEvidence = e;
            }
        }
        assertThat(dbEvidence).as("report must include DB_HEALTH evidence").isNotNull();
        assertThat(dbEvidence.get("description").asText())
                .contains("active=7").contains("idle=13").contains("waiting=2");

        // timeline references the same evidence id
        boolean dbInTimeline = false;
        for (JsonNode t : report.get("timeline")) {
            if ("dbhealth-1".equals(t.path("evidenceId").asText())) {
                dbInTimeline = true;
            }
        }
        assertThat(dbInTimeline).as("DB_HEALTH must appear on the timeline").isTrue();
    }
}
