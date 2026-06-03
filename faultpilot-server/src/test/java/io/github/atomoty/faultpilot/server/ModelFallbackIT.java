package io.github.atomoty.faultpilot.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.core.adapter.DiagnosisModel;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * When the model throws (transport/timeout/invalid output), the diagnosis must degrade to a
 * rule-only report rather than failing the request (specification.md §3, design §20.3).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModelFallbackIT {

    @TestConfiguration
    static class FailingModelConfig {
        @Bean
        @Primary
        DiagnosisModel failingModel() {
            return new DiagnosisModel() {
                @Override
                public String name() {
                    return "failing-test-model";
                }

                @Override
                public io.github.atomoty.faultpilot.core.model.ModelOutput generate(
                        io.github.atomoty.faultpilot.core.model.DiagnosisContext context) {
                    throw new RuntimeException("simulated model outage");
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void modelFailureProducesRuleFallbackReport() throws Exception {
        String body = """
                {"projectId":"order-service","environment":"staging","question":"q",
                 "from":"2026-06-01T01:00:00Z","to":"2026-06-01T03:00:00Z"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode report = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(report.get("ruleFallback").asBoolean()).isTrue();
        // Rule-derived root cause survives the model outage (staging demo => deployment-regression).
        assertThat(report.get("rootCauseCandidates")).isNotEmpty();
        assertThat(report.get("disclaimer").asText()).isNotBlank();
        // The failed model is named as an unavailable source, not only flagged by ruleFallback (P2).
        boolean modelMarked = false;
        for (JsonNode s : report.get("unavailableSources")) {
            if (s.asText().startsWith("model:")) {
                modelMarked = true;
            }
        }
        assertThat(modelMarked).as("failed model must appear in unavailableSources").isTrue();
    }
}
