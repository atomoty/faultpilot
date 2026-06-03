package io.github.atomoty.faultpilot.adapters.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.atomoty.faultpilot.adapters.ai.ModelUnavailableException;
import io.github.atomoty.faultpilot.core.model.DiagnosisContext;
import io.github.atomoty.faultpilot.core.model.DiagnosisRequest;
import io.github.atomoty.faultpilot.core.model.LogCluster;
import io.github.atomoty.faultpilot.core.model.ModelOutput;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodexCliDiagnosisModelTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final CodexConfig config = new CodexConfig("codex", "gpt-test", Duration.ofSeconds(10));

    private static final String OUTPUT_JSON = """
            {"summary":"NPE spike","rootCauseCandidates":[{"label":"deployment-regression",
             "title":"t","explanation":"e","evidenceIds":["log-1"]}],"recommendedActions":["check commit"]}
            """;

    private DiagnosisContext context() {
        DiagnosisRequest req = new DiagnosisRequest("p", "local", "why slow?",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T01:00:00Z"));
        LogCluster cluster = new LogCluster("log-1", "k", "NullPointerException", "boom", "Foo.bar",
                "ERROR", 6, 1, true, Instant.parse("2026-06-01T00:30:00Z"), Instant.parse("2026-06-01T00:40:00Z"), null);
        return new DiagnosisContext(req, List.of(cluster), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null);
    }

    /** Index of the value following a flag in the command, or -1. */
    private static String valueAfter(List<String> command, String flag) {
        int i = command.indexOf(flag);
        return i >= 0 && i + 1 < command.size() ? command.get(i + 1) : null;
    }

    @Test
    void successWritesOutputAndParses() {
        AtomicReference<List<String>> capturedCommand = new AtomicReference<>();
        ProcessRunner runner = (command, stdin, workingDir, timeout) -> {
            capturedCommand.set(command);
            Files.writeString(Path.of(valueAfter(command, "--output-last-message")), OUTPUT_JSON);
            return new ProcessRunner.Result(0, "");
        };

        ModelOutput out = new CodexCliDiagnosisModel(config, runner, mapper).generate(context());

        assertThat(out.summary()).isEqualTo("NPE spike");
        assertThat(out.rootCauseCandidates()).hasSize(1);
        assertThat(out.rootCauseCandidates().get(0).evidenceIds()).containsExactly("log-1");

        List<String> cmd = capturedCommand.get();
        assertThat(cmd.get(0)).isEqualTo("codex");
        assertThat(cmd).containsSubsequence("exec", "--ephemeral", "--skip-git-repo-check");
        assertThat(valueAfter(cmd, "--sandbox")).isEqualTo("read-only");
        assertThat(valueAfter(cmd, "-m")).isEqualTo("gpt-test");
        assertThat(cmd).contains("--output-schema", "--output-last-message");
        // Never touches login or credential files.
        assertThat(cmd).doesNotContain("login");
        assertThat(String.join(" ", cmd)).doesNotContain("auth.json").doesNotContain(".codex");
    }

    @Test
    void passesContextOnStdin() {
        AtomicReference<String> stdin = new AtomicReference<>();
        ProcessRunner runner = (command, in, workingDir, timeout) -> {
            stdin.set(in);
            Files.writeString(Path.of(valueAfter(command, "--output-last-message")), OUTPUT_JSON);
            return new ProcessRunner.Result(0, "");
        };

        new CodexCliDiagnosisModel(config, runner, mapper).generate(context());

        assertThat(stdin.get()).contains("log-1");      // evidence id reached the model input
        assertThat(stdin.get()).contains("why slow?");
    }

    @Test
    void nonZeroExitThrows() {
        ProcessRunner runner = (command, stdin, workingDir, timeout) -> new ProcessRunner.Result(1, "boom");
        assertThatThrownBy(() -> new CodexCliDiagnosisModel(config, runner, mapper).generate(context()))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void missingOutputFileThrows() {
        ProcessRunner runner = (command, stdin, workingDir, timeout) -> new ProcessRunner.Result(0, "");
        assertThatThrownBy(() -> new CodexCliDiagnosisModel(config, runner, mapper).generate(context()))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void invalidOutputJsonThrows() {
        ProcessRunner runner = (command, stdin, workingDir, timeout) -> {
            Files.writeString(Path.of(valueAfter(command, "--output-last-message")), "not-json");
            return new ProcessRunner.Result(0, "");
        };
        assertThatThrownBy(() -> new CodexCliDiagnosisModel(config, runner, mapper).generate(context()))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void timeoutThrows() {
        ProcessRunner runner = (command, stdin, workingDir, timeout) -> {
            throw new ProcessRunner.ProcessTimeoutException("timed out");
        };
        assertThatThrownBy(() -> new CodexCliDiagnosisModel(config, runner, mapper).generate(context()))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void cleansUpTempDirectory() {
        AtomicReference<Path> workDir = new AtomicReference<>();
        ProcessRunner runner = (command, stdin, dir, timeout) -> {
            workDir.set(dir);
            Files.writeString(Path.of(valueAfter(command, "--output-last-message")), OUTPUT_JSON);
            return new ProcessRunner.Result(0, "");
        };

        new CodexCliDiagnosisModel(config, runner, mapper).generate(context());

        assertThat(workDir.get()).isNotNull();
        assertThat(Files.exists(workDir.get())).as("temp work dir must be cleaned up").isFalse();
    }
}
