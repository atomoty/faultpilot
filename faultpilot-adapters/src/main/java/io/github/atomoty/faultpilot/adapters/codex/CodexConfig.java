package io.github.atomoty.faultpilot.adapters.codex;

import java.time.Duration;

/**
 * Resolved configuration for the experimental local Codex CLI provider (design §20.5).
 *
 * @param command the codex executable name or path (default {@code codex})
 * @param model   optional model id passed via {@code -m}; null/blank lets Codex use its default
 * @param timeout max wall-clock time for one {@code codex exec} invocation
 */
public record CodexConfig(String command, String model, Duration timeout) {

    public CodexConfig {
        command = (command == null || command.isBlank()) ? "codex" : command;
        timeout = timeout == null ? Duration.ofSeconds(120) : timeout;
    }
}
