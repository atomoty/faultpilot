package io.github.atomoty.faultpilot.server;

import io.github.atomoty.faultpilot.core.adapter.DiagnosisModel;
import io.github.atomoty.faultpilot.core.model.DiagnosisContext;
import io.github.atomoty.faultpilot.core.model.ModelOutput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rules — not the model — decide which root causes a report contains.
 *
 * <p>Found by the quality eval: a cautious model that judged "a deploy 20 minutes earlier is only
 * correlation" returned zero candidates, which silently dropped the {@code deployment-regression}
 * finding the rules had derived from real evidence. A model must not be able to delete a rule
 * finding, so this pins the behaviour with a model that answers with nothing at all.
 */
@SpringBootTest(properties = {
        "faultpilot.ingestion.require-token=false"
})
@AutoConfigureMockMvc
class RuleCandidatesSurviveModelIT {

    @TestConfiguration
    static class SilentModelConfig {
        /** Answers successfully but proposes no candidates — the cautious-model case. */
        @Bean
        @Primary
        DiagnosisModel silentModel() {
            return new DiagnosisModel() {
                @Override
                public String name() {
                    return "silent-test-model";
                }

                @Override
                public ModelOutput generate(DiagnosisContext context) {
                    return new ModelOutput("模型未给出候选", List.of(), List.of());
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void ruleCandidateSurvivesAModelThatProposesNothing() throws Exception {
        String body = """
                {"projectId":"order-service","environment":"staging","question":"什么问题?",
                 "from":"2026-06-01T01:00:00Z","to":"2026-06-01T03:00:00Z"}""";

        mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                // The model said nothing, yet the rule finding and its evidence are still reported.
                .andExpect(jsonPath("$.ruleFallback").value(false))
                .andExpect(jsonPath("$.rootCauseCandidates[0].label").value("deployment-regression"))
                .andExpect(jsonPath("$.rootCauseCandidates[0].strength").value("STRONG"))
                .andExpect(jsonPath("$.rootCauseCandidates[0].evidenceIds").isNotEmpty());
    }
}
