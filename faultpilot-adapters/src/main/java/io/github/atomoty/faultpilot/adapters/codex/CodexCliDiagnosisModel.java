package io.github.atomoty.faultpilot.adapters.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.adapters.ai.DiagnosisPromptBuilder;
import io.github.atomoty.faultpilot.adapters.ai.ModelOutputParser;
import io.github.atomoty.faultpilot.adapters.ai.ModelUnavailableException;
import io.github.atomoty.faultpilot.adapters.ai.OutputSchema;
import io.github.atomoty.faultpilot.core.adapter.DiagnosisModel;
import io.github.atomoty.faultpilot.core.model.DiagnosisContext;
import io.github.atomoty.faultpilot.core.model.ModelOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Experimental local provider that reuses an already-authenticated Codex CLI (design §20.5). It runs
 * {@code codex exec} non-interactively in a read-only sandbox, passing the sanitized context on stdin
 * and constraining the final message to {@link OutputSchema} via {@code --output-schema}; the result
 * is read from the {@code --output-last-message} file.
 *
 * <p>Security: this never runs {@code codex login} and never reads, copies, or logs Codex credential
 * files. It only invokes the already-logged-in {@code codex exec}. Any failure throws
 * {@link ModelUnavailableException} so the service falls back to a rule-only report.
 */
public class CodexCliDiagnosisModel implements DiagnosisModel {

    private static final Logger log = LoggerFactory.getLogger(CodexCliDiagnosisModel.class);

    private final CodexConfig config;
    private final ProcessRunner processRunner;
    private final DiagnosisPromptBuilder prompts;
    private final ModelOutputParser parser;

    public CodexCliDiagnosisModel(CodexConfig config, ProcessRunner processRunner, ObjectMapper mapper) {
        this.config = config;
        this.processRunner = processRunner;
        this.prompts = new DiagnosisPromptBuilder(mapper);
        this.parser = new ModelOutputParser(mapper);
    }

    @Override
    public String name() {
        return "codex-cli";
    }

    @Override
    public ModelOutput generate(DiagnosisContext context) {
        Path workDir = createTempDir();
        try {
            Path schemaFile = workDir.resolve("schema.json");
            Path outFile = workDir.resolve("out.json");
            Files.writeString(schemaFile, OutputSchema.JSON);

            List<String> command = buildCommand(schemaFile, outFile);
            ProcessRunner.Result result;
            try {
                result = processRunner.run(command, prompts.userPrompt(context), workDir, config.timeout());
            } catch (ProcessRunner.ProcessTimeoutException e) {
                throw new ModelUnavailableException("Codex CLI timed out", e);
            } catch (IOException e) {
                throw new ModelUnavailableException("Codex CLI could not be started", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ModelUnavailableException("Codex CLI interrupted", e);
            }

            if (result.exitCode() != 0) {
                log.warn("Codex CLI exited with {}: {}", result.exitCode(), result.stderr());
                throw new ModelUnavailableException("Codex CLI exited with " + result.exitCode());
            }
            if (!Files.isReadable(outFile)) {
                throw new ModelUnavailableException("Codex CLI produced no output file");
            }
            return parser.parse(Files.readString(outFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ModelUnavailableException("Codex CLI I/O failure", e);
        } finally {
            deleteRecursively(workDir);
        }
    }

    /**
     * {@code codex exec --ephemeral --skip-git-repo-check --sandbox read-only [-m model]
     * --output-schema schema.json --output-last-message out.json "<systemPrompt>"}.
     * The evidence context is passed on stdin; the system prompt is the instruction argument.
     */
    private List<String> buildCommand(Path schemaFile, Path outFile) {
        List<String> command = new ArrayList<>();
        command.add(config.command());
        command.add("exec");
        command.add("--ephemeral");
        command.add("--skip-git-repo-check");
        command.add("--sandbox");
        command.add("read-only");
        if (config.model() != null && !config.model().isBlank()) {
            command.add("-m");
            command.add(config.model());
        }
        command.add("--output-schema");
        command.add(schemaFile.toString());
        command.add("--output-last-message");
        command.add(outFile.toString());
        command.add(prompts.systemPrompt());
        return command;
    }

    private Path createTempDir() {
        try {
            return Files.createTempDirectory("faultpilot-codex-");
        } catch (IOException e) {
            throw new ModelUnavailableException("Could not create Codex work directory", e);
        }
    }

    private void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
