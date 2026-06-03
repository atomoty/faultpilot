package io.github.atomoty.faultpilot.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end local-file log diagnosis (development-plan.md §6.2): a project configured with
 * {@code logs.type=local-file} reads a sample log, and the report contains the parsed exception
 * cluster with intact evidence references.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("localfileit")
class LocalFileDiagnosisIT {

    @DynamicPropertySource
    static void registerSampleLogPath(DynamicPropertyRegistry registry) {
        // The localfileit profile defines the project list; only the absolute sample path is dynamic.
        Path sample = Path.of("src/test/resources/sample-it.txt").toAbsolutePath();
        registry.add("sample.log.path", sample::toString);
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void diagnosesExceptionClusterFromLogFile() throws Exception {
        String body = """
                {"projectId":"logfile-it","environment":"local","question":"最近的错误是什么?",
                 "from":"2026-06-01T00:00:00Z","to":"2026-06-01T23:59:59Z"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode report = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(report.get("ruleFallback").asBoolean()).isFalse();

        // evidence contains a log cluster describing the NPE
        JsonNode evidence = report.get("evidence");
        assertThat(evidence).isNotEmpty();
        boolean npeCluster = false;
        Set<String> evidenceIds = new HashSet<>();
        for (JsonNode e : evidence) {
            evidenceIds.add(e.get("evidenceId").asText());
            if ("LOG_CLUSTER".equals(e.get("type").asText())
                    && e.get("description").asText().contains("NullPointerException")) {
                npeCluster = true;
            }
        }
        assertThat(npeCluster).as("report must include a NullPointerException log cluster").isTrue();

        // reference integrity: timeline ids resolve to evidence
        for (JsonNode t : report.get("timeline")) {
            if (t.hasNonNull("evidenceId")) {
                assertThat(evidenceIds).contains(t.get("evidenceId").asText());
            }
        }
    }
}
