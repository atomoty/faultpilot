package io.github.atomoty.faultpilot.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.core.model.DiagnosisReport;
import io.github.atomoty.faultpilot.server.repository.DiagnosisReportRepository;
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
 * Regression for the restart id-collision: the diagnosis counter is in-memory and resets on
 * restart while reports are persisted, so a freshly generated id can equal one already in the
 * store — and the MERGE-based save would silently overwrite it. nextId() must skip occupied ids.
 *
 * <p>Simulated here without a restart: pre-seed the id the counter would produce next, then run a
 * diagnosis and assert it skipped past the seeded id and the seeded report survived unchanged.
 */
@SpringBootTest(properties = {
        "faultpilot.ai.provider=mock",
        "faultpilot.ingestion.require-token=false"
})
@AutoConfigureMockMvc
class DiagnosisIdCollisionIT {

    private static final String RUN = """
            {"projectId":"order-service","environment":"local","question":"why slow?",
             "from":"2026-06-01T01:00:00Z","to":"2026-06-01T03:00:00Z"}""";
    private static final String MARKER = "preseeded report - must survive";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    DiagnosisReportRepository repository;

    @Test
    void occupiedIdIsSkippedAndStoredReportSurvives() throws Exception {
        // Establish the counter's current position: diag-<day>-<n>.
        String firstId = runDiagnosis();
        String prefix = firstId.substring(0, firstId.lastIndexOf('-') + 1);
        int n = Integer.parseInt(firstId.substring(firstId.lastIndexOf('-') + 1));

        // Occupy the id the counter would produce next, as a same-day restart would have.
        String occupied = prefix + String.format("%03d", n + 1);
        repository.save(new DiagnosisReport(occupied, "order-service", "local", MARKER,
                List.of(), List.of(), List.of(), List.of(), List.of(), null, false,
                DiagnosisReport.DISCLAIMER, Instant.now()));

        String secondId = runDiagnosis();

        assertThat(secondId).isEqualTo(prefix + String.format("%03d", n + 2));
        assertThat(repository.find(occupied))
                .hasValueSatisfying(r -> assertThat(r.summary()).isEqualTo(MARKER));
    }

    private String runDiagnosis() throws Exception {
        String response = mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON).content(RUN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("diagnosisId").asText();
    }
}
