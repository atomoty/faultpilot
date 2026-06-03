package io.github.atomoty.faultpilot.adapters.codex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Production {@link ProcessRunner} using {@link ProcessBuilder}. Force-kills the process on timeout.
 * The child inherits the current environment (so an already logged-in Codex CLI works); this runner
 * never reads or copies credential files.
 *
 * <p>stdout and stderr are drained on separate threads while stdin is written, so a child that emits
 * a lot of output (codex exec emits JSONL events) cannot fill a pipe buffer and deadlock — which
 * would otherwise prevent the timeout from ever firing.
 */
public final class JdkProcessRunner implements ProcessRunner {

    private static final int MAX_STDERR_CHARS = 2000;

    @Override
    public Result run(List<String> command, String stdin, Path workingDir, Duration timeout)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(false);
        Process process = pb.start();

        // Drain both streams concurrently so the child never blocks on a full pipe buffer.
        StreamDrainer outDrainer = new StreamDrainer(process.getInputStream());
        StreamDrainer errDrainer = new StreamDrainer(process.getErrorStream());
        Thread outThread = new Thread(outDrainer, "codex-stdout");
        Thread errThread = new Thread(errDrainer, "codex-stderr");
        outThread.setDaemon(true);
        errThread.setDaemon(true);
        outThread.start();
        errThread.start();

        Thread stdinThread = new Thread(() -> {
            try (OutputStream stdinStream = process.getOutputStream()) {
                if (stdin != null) {
                    stdinStream.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // process may have exited early; exit-code/output checks handle it
            }
        }, "codex-stdin");
        stdinThread.setDaemon(true);
        stdinThread.start();

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            stdinThread.interrupt();
            throw new ProcessTimeoutException("Process timed out after " + timeout);
        }
        // Process exited: let the drainers finish reading any buffered tail.
        outThread.join(1000);
        errThread.join(1000);

        String stderr = errDrainer.text();
        if (stderr.length() > MAX_STDERR_CHARS) {
            stderr = stderr.substring(0, MAX_STDERR_CHARS) + "…";
        }
        return new Result(process.exitValue(), stderr);
    }

    /** Reads a stream to a string on its own thread; failures are swallowed (best-effort). */
    private static final class StreamDrainer implements Runnable {
        private final InputStream stream;
        private volatile String text = "";

        StreamDrainer(InputStream stream) {
            this.stream = stream;
        }

        @Override
        public void run() {
            try {
                text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // best-effort; partial/empty is fine
            }
        }

        String text() {
            return text;
        }
    }
}
