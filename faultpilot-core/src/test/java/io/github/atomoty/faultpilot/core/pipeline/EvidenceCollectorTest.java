package io.github.atomoty.faultpilot.core.pipeline;

import io.github.atomoty.faultpilot.core.adapter.LogSourceAdapter;
import io.github.atomoty.faultpilot.core.model.DatabaseHealthSnapshot;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceCollectorTest {

    private EvidenceQuery query() {
        Instant now = Instant.now();
        return new EvidenceQuery("p", "e", now.minusSeconds(3600), now, 100);
    }

    @Test
    void slowSourceTimesOutAndIsMarkedUnavailableWithoutFailing() {
        ExecutorService exec = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), new ThreadPoolExecutor.AbortPolicy());
        LogSourceAdapter slow = q -> {
            sleep(2000);
            return List.of();
        };

        EvidenceCollector collector = new EvidenceCollector(
                List.of(slow), List.of(), List.of(), List.of(), List.of(),
                Duration.ofMillis(200), exec);

        CollectedEvidence result = collector.collect(query());

        assertThat(result.logs()).isEmpty();
        assertThat(result.unavailableSources()).anyMatch(s -> s.contains("timeout"));
        exec.shutdownNow();
    }

    @Test
    void throwingSourceIsMarkedUnavailable() {
        // Review H2: a source that throws (e.g. all log files unreadable) must be reported as
        // unavailable, not silently absent.
        ExecutorService exec = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), new ThreadPoolExecutor.AbortPolicy());
        LogSourceAdapter failing = q -> {
            throw new io.github.atomoty.faultpilot.core.log.LogSourceUnavailableException("no files");
        };

        EvidenceCollector collector = new EvidenceCollector(
                List.of(failing), List.of(), List.of(), List.of(), List.of(),
                Duration.ofSeconds(2), exec);

        CollectedEvidence result = collector.collect(query());

        assertThat(result.logs()).isEmpty();
        assertThat(result.unavailableSources()).anyMatch(s -> s.contains("error"));
        exec.shutdownNow();
    }

    @Test
    void saturatedPoolRejectsAndDegradesInsteadOfThrowing() {
        // Pool of 1 with no spare queue: the first source occupies the worker for a short while,
        // so concurrently-submitted sources are rejected at submit time.
        ExecutorService exec = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new java.util.concurrent.SynchronousQueue<>(), new ThreadPoolExecutor.AbortPolicy());

        LogSourceAdapter occupying = q -> {
            sleep(300); // self-releasing: keeps the single worker busy during submission
            return List.of(sampleLog());
        };
        LogSourceAdapter second = q -> List.of(sampleLog());
        LogSourceAdapter third = q -> List.of(sampleLog());

        EvidenceCollector collector = new EvidenceCollector(
                List.of(occupying, second, third), List.of(), List.of(), List.of(), List.of(),
                Duration.ofSeconds(2), exec);

        // Must not throw RejectedExecutionException; saturated sources degrade to unavailable.
        CollectedEvidence result = collector.collect(query());

        assertThat(result.unavailableSources()).anyMatch(s -> s.contains("rejected"));
        exec.shutdownNow();
    }

    @Test
    void unavailableDatabaseHealthSnapshotIsRecordedAndKept() {
        ExecutorService exec = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), new ThreadPoolExecutor.AbortPolicy());

        EvidenceCollector collector = new EvidenceCollector(
                List.of(), List.of(), List.of(), List.of(),
                List.of(q -> DatabaseHealthSnapshot.unavailable("MySQL unavailable")),
                Duration.ofSeconds(2), exec);

        CollectedEvidence result = collector.collect(query());

        assertThat(result.databaseHealth()).hasSize(1);
        assertThat(result.databaseHealth().get(0).available()).isFalse();
        assertThat(result.unavailableSources()).anyMatch(s -> s.startsWith("db-health:"));
        exec.shutdownNow();
    }

    @Test
    void noDatabaseConfiguredSnapshotIsIgnored() {
        ExecutorService exec = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), new ThreadPoolExecutor.AbortPolicy());

        EvidenceCollector collector = new EvidenceCollector(
                List.of(), List.of(), List.of(), List.of(),
                List.of(q -> DatabaseHealthSnapshot.withoutDatabaseConfig()),
                Duration.ofSeconds(2), exec);

        CollectedEvidence result = collector.collect(query());

        assertThat(result.databaseHealth()).isEmpty();
        assertThat(result.unavailableSources()).isEmpty();
        exec.shutdownNow();
    }

    private LogEvent sampleLog() {
        return new LogEvent("p", "e", Instant.now(), "ERROR", "l", "t", "m", null, null);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
