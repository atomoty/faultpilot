package io.github.atomoty.faultpilot.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.adapters.mock.DemoFixtures;
import io.github.atomoty.faultpilot.adapters.mock.DemoScenario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end mock flow over the REST API, asserting the specification.md §11 acceptance contract:
 * valid JSON, expected root-cause label hit, required evidence ids referenced, no evidence-less root cause.
 */
@SpringBootTest(properties = "faultpilot.ingestion.require-token=false")
@AutoConfigureMockMvc
class DiagnosisFlowIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private String requestBody(DemoScenario scenario) {
        String from = DemoFixtures.ANCHOR.minus(Duration.ofHours(2)).toString();
        String to = DemoFixtures.ANCHOR.toString();
        return """
                {"projectId":"%s","environment":"%s","question":"为什么出问题?","from":"%s","to":"%s"}
                """.formatted(scenario.projectId(), scenario.environment(), from, to);
    }

    @Test
    void bothDemoScenariosHitExpectedRootCauseWithEvidence() throws Exception {
        for (DemoScenario scenario : DemoFixtures.all()) {
            MvcResult result = mockMvc.perform(post("/api/v1/diagnoses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody(scenario)))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode report = objectMapper.readTree(result.getResponse().getContentAsString());

            // valid structure
            assertThat(report.hasNonNull("diagnosisId")).isTrue();
            assertThat(report.hasNonNull("summary")).isTrue();
            assertThat(report.get("ruleFallback").asBoolean()).isFalse();
            assertThat(report.get("disclaimer").asText()).isNotBlank();

            JsonNode candidates = report.get("rootCauseCandidates");
            assertThat(candidates).isNotEmpty();

            // expected label hit
            boolean labelHit = false;
            for (JsonNode c : candidates) {
                // no evidence-less root cause
                assertThat(c.get("evidenceIds")).isNotEmpty();
                // strength is a rule enum, never a numeric probability
                assertThat(c.get("strength").asText()).isIn("STRONG", "MODERATE", "WEAK");
                if (scenario.expectedRootCauseLabel().equals(c.get("label").asText())) {
                    labelHit = true;
                    for (String required : scenario.requiredEvidenceIds()) {
                        assertThat(c.get("evidenceIds").toString()).contains(required);
                    }
                }
            }
            assertThat(labelHit)
                    .as("scenario %s/%s must hit %s",
                            scenario.projectId(), scenario.environment(), scenario.expectedRootCauseLabel())
                    .isTrue();

            // reference integrity: every referenced id resolves to an entry in evidence[] (#4)
            java.util.Set<String> evidenceIds = new java.util.HashSet<>();
            for (JsonNode e : report.get("evidence")) {
                evidenceIds.add(e.get("evidenceId").asText());
            }
            for (JsonNode c : candidates) {
                for (JsonNode id : c.get("evidenceIds")) {
                    assertThat(evidenceIds).contains(id.asText());
                }
            }
            for (JsonNode t : report.get("timeline")) {
                if (t.hasNonNull("evidenceId")) {
                    assertThat(evidenceIds).contains(t.get("evidenceId").asText());
                }
            }
        }
    }

    @Test
    void rejectsRangeExceedingMaxQueryHours() throws Exception {
        // max-query-hours is 24; ask for 48h
        String from = DemoFixtures.ANCHOR.minus(Duration.ofHours(48)).toString();
        String to = DemoFixtures.ANCHOR.toString();
        String body = """
                {"projectId":"order-service","environment":"local","question":"q","from":"%s","to":"%s"}
                """.formatted(from, to);

        mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsRangeJustOverMaxQueryHours() throws Exception {
        // 24h00m01s must be rejected (review #7: no hour rounding).
        String from = DemoFixtures.ANCHOR.minus(Duration.ofHours(24).plusSeconds(1)).toString();
        String to = DemoFixtures.ANCHOR.toString();
        String body = """
                {"projectId":"order-service","environment":"local","question":"q","from":"%s","to":"%s"}
                """.formatted(from, to);

        mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void writtenEventSecretsAreRedactedInReport() throws Exception {
        // This test explicitly enables local-demo open ingestion; packaged defaults stay secure.
        String eventBody = """
                {"projectId":"order-service","environment":"local","type":"CONFIG_CHANGE",
                 "occurredAt":"%s","attributes":{"password":"plain-secret","key":"timeout"}}
                """.formatted(DemoFixtures.ANCHOR.minus(Duration.ofMinutes(15)).toString());
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody))
                .andExpect(status().isCreated());

        String from = DemoFixtures.ANCHOR.minus(Duration.ofHours(2)).toString();
        String to = DemoFixtures.ANCHOR.toString();
        String diagBody = """
                {"projectId":"order-service","environment":"local","question":"q","from":"%s","to":"%s"}
                """.formatted(from, to);
        MvcResult result = mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON).content(diagBody))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("plain-secret");
        assertThat(json).contains("timeout"); // non-secret attribute preserved
    }

    @Test
    void rejectsUnknownProject() throws Exception {
        String from = DemoFixtures.ANCHOR.minus(Duration.ofHours(1)).toString();
        String to = DemoFixtures.ANCHOR.toString();
        String body = """
                {"projectId":"nope","environment":"local","question":"q","from":"%s","to":"%s"}
                """.formatted(from, to);

        mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
