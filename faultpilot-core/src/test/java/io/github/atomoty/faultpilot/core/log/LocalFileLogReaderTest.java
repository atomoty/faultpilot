package io.github.atomoty.faultpilot.core.log;

import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileLogReaderTest {

    private final LocalFileLogReader reader = new LocalFileLogReader(new LogLineParser());

    // The sample timestamps are local times; interpret them as UTC for deterministic assertions.
    private LocalFileLogSource sourceFor(Path file) {
        return new LocalFileLogSource(List.of(file.toString()), null, StandardCharsets.UTF_8, ZoneOffset.UTC);
    }

    private EvidenceQuery query(Instant from, Instant to, int max) {
        return new EvidenceQuery("p", "local", from, to, max);
    }

    private Path write(Path dir, String name, String content) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    @Test
    void aggregatesMultiLineStackIntoSingleEvent(@TempDir Path dir) throws IOException {
        String log = """
                2026-06-01 10:05:31.501 ERROR [exec-5] com.example.order.OrderService - Create order failed
                java.lang.NullPointerException: order is null
                \tat com.example.order.OrderService.create(OrderService.java:88)
                \tat com.example.order.OrderController.create(OrderController.java:42)
                2026-06-01 10:12:05.410 INFO  [sched-1] com.example.Health - ok
                """;
        Path file = write(dir, "app.log", log);

        List<LogEvent> events = reader.read(sourceFor(file),
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500));

        assertThat(events).hasSize(1); // INFO dropped, one ERROR event
        LogEvent e = events.get(0);
        assertThat(e.level()).isEqualTo("ERROR");
        assertThat(e.attributes()).containsEntry("exceptionClass", "NullPointerException");
        assertThat(e.stackTrace()).contains("OrderService.java:88").contains("OrderController.java:42");
    }

    @Test
    void filtersByTimeRange(@TempDir Path dir) throws IOException {
        String log = """
                2026-06-01 08:00:00.000 ERROR [t] com.example.A - early failure
                2026-06-01 10:00:00.000 ERROR [t] com.example.B - in-window failure
                """;
        Path file = write(dir, "app.log", log);

        List<LogEvent> events = reader.read(sourceFor(file),
                query(Instant.parse("2026-06-01T09:00:00Z"), Instant.parse("2026-06-01T11:00:00Z"), 500));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).message()).isEqualTo("in-window failure");
    }

    @Test
    void dropsInfoAndDebugKeepsWarnAndError(@TempDir Path dir) throws IOException {
        String log = """
                2026-06-01 10:00:00.000 DEBUG [t] com.example.A - noisy
                2026-06-01 10:00:01.000 INFO  [t] com.example.A - fyi
                2026-06-01 10:00:02.000 WARN  [t] com.example.A - careful
                2026-06-01 10:00:03.000 ERROR [t] com.example.A - boom
                """;
        Path file = write(dir, "app.log", log);

        List<LogEvent> events = reader.read(sourceFor(file),
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500));

        assertThat(events).extracting(LogEvent::level).containsExactlyInAnyOrder("WARN", "ERROR");
    }

    @Test
    void capsToMostRecentMaxResults(@TempDir Path dir) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("2026-06-01 10:00:0").append(i).append(".000 ERROR [t] com.example.A - e").append(i).append('\n');
        }
        Path file = write(dir, "app.log", sb.toString());

        List<LogEvent> events = reader.read(sourceFor(file),
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 3));

        assertThat(events).hasSize(3);
        // most recent kept (e9, e8, e7)
        assertThat(events).extracting(LogEvent::message).contains("e9", "e8", "e7");
    }

    @Test
    void allFilesUnreadableThrowsUnavailable(@TempDir Path dir) {
        LocalFileLogSource source = new LocalFileLogSource(
                List.of(dir.resolve("does-not-exist.log").toString()), null, StandardCharsets.UTF_8, ZoneOffset.UTC);

        // Review H2: total read failure must surface as unavailable, not a misleading empty result.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reader.read(source,
                        query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500)))
                .isInstanceOf(io.github.atomoty.faultpilot.core.log.LogSourceUnavailableException.class);
    }

    @Test
    void partialReadFailureReturnsWhatWasRead(@TempDir Path dir) throws IOException {
        Path good = write(dir, "good.log",
                "2026-06-01 10:00:00.000 ERROR [t] com.example.A - boom\n");
        LocalFileLogSource source = new LocalFileLogSource(
                List.of(dir.resolve("missing.log").toString(), good.toString()),
                null, StandardCharsets.UTF_8, ZoneOffset.UTC);

        List<LogEvent> events = reader.read(source,
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500));

        // One file readable → return its events, do not throw.
        assertThat(events).hasSize(1);
        assertThat(events.get(0).message()).isEqualTo("boom");
    }

    @Test
    void parsesIsoTSeparatedTimestamp(@TempDir Path dir) throws IOException {
        // Review M3: LogLineParser accepts 'T'; the reader must parse it too (not silently drop).
        Path file = write(dir, "iso.log",
                "2026-06-01T10:00:00.500 ERROR [t] com.example.A - iso failure\n");
        List<LogEvent> events = reader.read(sourceFor(file),
                query(Instant.parse("2026-06-01T09:00:00Z"), Instant.parse("2026-06-01T11:00:00Z"), 500));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).message()).isEqualTo("iso failure");
        assertThat(events.get(0).occurredAt()).isEqualTo(Instant.parse("2026-06-01T10:00:00.500Z"));
    }

    @Test
    void parsesSpringBootOffsetTimestamp(@TempDir Path dir) throws IOException {
        Path file = write(dir, "boot.log",
                "2026-06-01T10:00:00.500+08:00 ERROR 12345 --- [main] com.example.A : boot failure\n");
        List<LogEvent> events = reader.read(sourceFor(file),
                query(Instant.parse("2026-06-01T01:00:00Z"), Instant.parse("2026-06-01T03:00:00Z"), 500));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).message()).isEqualTo("boot failure");
        assertThat(events.get(0).occurredAt()).isEqualTo(Instant.parse("2026-06-01T02:00:00.500Z"));
    }

    @Test
    void exceptionClassNotDerivedFromProseMessage(@TempDir Path dir) throws IOException {
        // A message merely mentioning an exception name, with no stack trace, must not set exceptionClass.
        Path file = write(dir, "prose.log",
                "2026-06-01 10:00:00.000 WARN  [t] com.example.A - Retrying after TimeoutException occurred\n");
        List<LogEvent> events = reader.read(sourceFor(file),
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).attributes()).doesNotContainKey("exceptionClass");
    }

    @Test
    void boundsStackTraceLines(@TempDir Path dir) throws IOException {
        StringBuilder sb = new StringBuilder(
                "2026-06-01 10:00:00.000 ERROR [t] com.example.A - deep stack\n");
        sb.append("java.lang.RuntimeException: boom\n");
        for (int i = 0; i < 5000; i++) {
            sb.append("\tat com.example.C.m").append(i).append("(C.java:").append(i).append(")\n");
        }
        Path file = write(dir, "deep.log", sb.toString());

        List<LogEvent> events = reader.read(sourceFor(file),
                query(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:59Z"), 500));

        assertThat(events).hasSize(1);
        String stack = events.get(0).stackTrace();
        // capped to MAX_STACK_LINES content lines, plus a trailing "…" marker line
        assertThat(stack.split("\\R").length).isLessThanOrEqualTo(LocalFileLogReader.MAX_STACK_LINES + 1);
        assertThat(stack).endsWith("…");
    }
}
