package io.github.atomoty.faultpilot.adapters.codex;

import java.nio.file.Path;
import java.time.Duration;

/**
 * A seam over launching an external process, so the Codex adapter can be unit-tested with a fake
 * that simulates exit codes / timeouts and writes the output file, without running the real CLI.
 */
public interface ProcessRunner {

    /** Result of a finished process: exit code and a (truncated) stderr for failure diagnostics. */
    record Result(int exitCode, String stderr) {
    }

    /**
     * Run {@code command} with {@code stdin} piped in, the given working directory, killing it if it
     * exceeds {@code timeout}.
     *
     * @throws ProcessTimeoutException if the process did not finish within the timeout
     * @throws java.io.IOException     if the process could not be started or I/O failed
     */
    Result run(java.util.List<String> command, String stdin, Path workingDir, Duration timeout)
            throws java.io.IOException, InterruptedException;

    /** Thrown when the process exceeds its timeout (and is force-killed). */
    class ProcessTimeoutException extends RuntimeException {
        public ProcessTimeoutException(String message) {
            super(message);
        }
    }
}
