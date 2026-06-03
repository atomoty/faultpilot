package io.github.atomoty.faultpilot.adapters.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.atomoty.faultpilot.adapters.ai.ModelUnavailableException;
import io.github.atomoty.faultpilot.core.model.DiagnosisContext;
import io.github.atomoty.faultpilot.core.model.DiagnosisRequest;
import io.github.atomoty.faultpilot.core.model.LogCluster;
import io.github.atomoty.faultpilot.core.model.ModelOutput;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiApiDiagnosisModelTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final OpenAiConfig config =
            new OpenAiConfig("https://api.openai.com", "sk-secret-key", "gpt-test", Duration.ofSeconds(5));

    private DiagnosisContext context() {
        DiagnosisRequest req = new DiagnosisRequest("p", "local", "why slow?",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T01:00:00Z"));
        LogCluster cluster = new LogCluster("log-1", "k", "NullPointerException", "boom", "Foo.bar",
                "ERROR", 6, 1, true, Instant.parse("2026-06-01T00:30:00Z"), Instant.parse("2026-06-01T00:40:00Z"), null);
        return new DiagnosisContext(req, List.of(cluster), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null);
    }

    /** A canned successful chat.completions response whose message.content is the schema JSON. */
    private String successBody() {
        return """
                {"choices":[{"message":{"content":
                  "{\\"summary\\":\\"NPE spike\\",\\"rootCauseCandidates\\":[{\\"label\\":\\"deployment-regression\\",\\"title\\":\\"t\\",\\"explanation\\":\\"e\\",\\"evidenceIds\\":[\\"log-1\\"]}],\\"recommendedActions\\":[\\"check commit\\"]}"
                }}]}
                """;
    }

    private HttpResponse<String> response(int status, String body) {
        return new StubHttpResponse(status, body);
    }

    @Test
    void parsesSuccessfulResponse() {
        OpenAiApiDiagnosisModel model = new OpenAiApiDiagnosisModel(
                config, req -> response(200, successBody()), mapper);

        ModelOutput out = model.generate(context());

        assertThat(out.summary()).isEqualTo("NPE spike");
        assertThat(out.rootCauseCandidates()).hasSize(1);
        assertThat(out.rootCauseCandidates().get(0).label()).isEqualTo("deployment-regression");
        assertThat(out.rootCauseCandidates().get(0).evidenceIds()).containsExactly("log-1");
        assertThat(out.recommendedActions()).containsExactly("check commit");
    }

    @Test
    void sendsWellFormedRequest() {
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        AtomicReference<String> bodyText = new AtomicReference<>();
        OpenAiApiDiagnosisModel model = new OpenAiApiDiagnosisModel(config, req -> {
            captured.set(req);
            bodyText.set(BodyPublishers.stringOf(req));
            return response(200, successBody());
        }, mapper);

        model.generate(context());

        HttpRequest req = captured.get();
        assertThat(req.uri().toString()).isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(req.headers().firstValue("Authorization")).contains("Bearer sk-secret-key");
        assertThat(bodyText.get()).contains("\"model\":\"gpt-test\"");
        assertThat(bodyText.get()).contains("json_schema");
        assertThat(bodyText.get()).contains("log-1"); // evidence id reached the user prompt
    }

    @Test
    void nonSuccessStatusThrowsAndDoesNotLeakKey() {
        OpenAiApiDiagnosisModel model = new OpenAiApiDiagnosisModel(
                config, req -> response(401, "{\"error\":\"unauthorized\"}"), mapper);

        assertThatThrownBy(() -> model.generate(context()))
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageNotContaining("sk-secret-key");
    }

    @Test
    void timeoutThrows() {
        OpenAiApiDiagnosisModel model = new OpenAiApiDiagnosisModel(config, req -> {
            throw new HttpTimeoutException("timeout");
        }, mapper);

        assertThatThrownBy(() -> model.generate(context()))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void emptyChoicesThrows() {
        OpenAiApiDiagnosisModel model = new OpenAiApiDiagnosisModel(
                config, req -> response(200, "{\"choices\":[]}"), mapper);

        assertThatThrownBy(() -> model.generate(context()))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void invalidContentJsonThrows() {
        String body = "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}";
        OpenAiApiDiagnosisModel model = new OpenAiApiDiagnosisModel(config, req -> response(200, body), mapper);

        assertThatThrownBy(() -> model.generate(context()))
                .isInstanceOf(ModelUnavailableException.class);
    }
}
