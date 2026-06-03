package io.github.atomoty.faultpilot.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Event-write authorization (review #1). With tokens configured, writes require a valid Bearer token
 * bound to the project and environment.
 */
@SpringBootTest(properties = {
        "faultpilot.ingestion.tokens[0].token=secret-token",
        "faultpilot.ingestion.tokens[0].project-id=order-service",
        "faultpilot.ingestion.tokens[0].environments[0]=local"
})
@AutoConfigureMockMvc
class IngestionAuthIT {

    @Autowired
    MockMvc mockMvc;

    private static final String BODY = """
            {"projectId":"order-service","environment":"local","type":"DEPLOYMENT",
             "occurredAt":"2026-06-01T02:00:00Z","attributes":{"version":"v1"}}
            """;

    @Test
    void rejectsMissingTokenWith401() throws Exception {
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongTokenWith403() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer nope")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsTokenBoundToOtherEnvironmentWith403() throws Exception {
        String stagingBody = BODY.replace("\"environment\":\"local\"", "\"environment\":\"staging\"");
        mockMvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer secret-token")
                        .contentType(MediaType.APPLICATION_JSON).content(stagingBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsValidBoundTokenWith201() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer secret-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }
}
