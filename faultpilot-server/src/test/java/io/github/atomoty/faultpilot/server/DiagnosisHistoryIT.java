package io.github.atomoty.faultpilot.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the diagnosis history endpoint: a run shows up in GET /api/v1/diagnoses, with summary,
 * and the project filter works. Uses the built-in mock {@code order-service} project.
 */
@SpringBootTest(properties = {
        "faultpilot.ai.provider=mock",
        "faultpilot.ingestion.require-token=false"
})
@AutoConfigureMockMvc
class DiagnosisHistoryIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private static final String RUN = """
            {"projectId":"order-service","environment":"local","question":"why slow?",
             "from":"2026-06-01T01:00:00Z","to":"2026-06-01T03:00:00Z"}""";

    @Test
    void diagnosisAppearsInHistoryWithSummary() throws Exception {
        String response = mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON).content(RUN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String diagnosisId = objectMapper.readTree(response).get("diagnosisId").asText();

        String history = mockMvc.perform(get("/api/v1/diagnoses").param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode rows = objectMapper.readTree(history);
        assertThat(rows.isArray()).isTrue();
        JsonNode mine = null;
        for (JsonNode row : rows) {
            if (diagnosisId.equals(row.get("diagnosisId").asText())) {
                mine = row;
                break;
            }
        }
        assertThat(mine).as("history should contain the run we just created").isNotNull();
        assertThat(mine.get("projectId").asText()).isEqualTo("order-service");
        assertThat(mine.get("environment").asText()).isEqualTo("local");
        assertThat(mine.hasNonNull("summary")).isTrue();
        assertThat(mine.hasNonNull("createdAt")).isTrue();
    }

    @Test
    void projectFilterExcludesNonMatching() throws Exception {
        mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON).content(RUN))
                .andExpect(status().isOk());

        // A project with no reports yields an empty list.
        mockMvc.perform(get("/api/v1/diagnoses").param("projectId", "no-such-project"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
