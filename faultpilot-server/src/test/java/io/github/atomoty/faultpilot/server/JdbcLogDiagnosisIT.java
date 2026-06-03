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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end JDBC log-table diagnosis (development-plan.md §6.3): a project configured with
 * {@code logs.type=jdbc} reads a read-only view and the report contains the parsed exception cluster.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jdbcit")
class JdbcLogDiagnosisIT {

    // Shared in-memory DB kept alive for the whole JVM via DB_CLOSE_DELAY=-1.
    private static final String URL =
            "jdbc:h2:mem:jdbcit;DB_CLOSE_DELAY=-1;INIT="
                    + "CREATE TABLE IF NOT EXISTS app_log(occurred_at TIMESTAMP, log_level VARCHAR(10),"
                    + " trace_id VARCHAR(64), message VARCHAR(1000), stack_trace VARCHAR(4000))"
                    + "\\;CREATE VIEW IF NOT EXISTS faultpilot_log_view AS SELECT occurred_at,"
                    + " log_level AS level, trace_id, message, stack_trace FROM app_log";

    @DynamicPropertySource
    static void jdbcUrl(DynamicPropertyRegistry registry) throws SQLException {
        registry.add("jdbc.it.url", () -> URL);
        seed();
    }

    private static void seed() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL); Statement st = c.createStatement()) {
            st.execute("DELETE FROM app_log");
            for (int i = 0; i < 5; i++) {
                st.execute("INSERT INTO app_log VALUES ('2026-06-01 10:0" + (5 + i)
                        + ":00.000','ERROR','t-" + i + "','Create order failed',"
                        + "'java.lang.NullPointerException: order is null"
                        + "\nat com.example.order.OrderService.create(OrderService.java:88)')");
            }
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void diagnosesExceptionClusterFromJdbcView() throws Exception {
        String body = """
                {"projectId":"jdbc-it","environment":"local","question":"最近的错误是什么?",
                 "from":"2026-06-01T00:00:00Z","to":"2026-06-01T23:59:59Z"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode report = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(report.get("ruleFallback").asBoolean()).isFalse();
        assertThat(report.get("unavailableSources")).isEmpty();

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

        for (JsonNode t : report.get("timeline")) {
            if (t.hasNonNull("evidenceId")) {
                assertThat(evidenceIds).contains(t.get("evidenceId").asText());
            }
        }
    }
}
