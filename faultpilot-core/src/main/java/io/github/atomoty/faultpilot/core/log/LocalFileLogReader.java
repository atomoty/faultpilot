package io.github.atomoty.faultpilot.core.log;

import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Reads local Java log files into normalized {@link LogEvent}s: aggregates multi-line stack traces,
 * parses timestamps, keeps only WARN/ERROR within the query time range, and keeps only the most
 * recent {@code maxResults} (development-plan.md §6.2, requirements.md §15).
 *
 * <p>Scanning is bounded so a huge in-range log cannot exhaust memory or the evidence thread pool
 * (review H1): each file is read up to {@link #MAX_BYTES_PER_FILE}, each event's stack trace is
 * capped, and only {@code maxResults} events are retained at any time.
 *
 * <p>If no configured file can be read at all, a {@link LogSourceUnavailableException} is thrown so
 * the source is reported as unavailable rather than as an empty (misleading) result (review H2).
 */
public class LocalFileLogReader {

    private static final Logger log = LoggerFactory.getLogger(LocalFileLogReader.class);
    private static final Set<String> KEPT_LEVELS = Set.of("WARN", "ERROR");

    /** Max bytes read per file before truncating the scan (defensive bound). */
    static final long MAX_BYTES_PER_FILE = 64L * 1024 * 1024; // 64 MB
    /** Max stack-trace lines retained per event. */
    static final int MAX_STACK_LINES = 100;
    /** Max characters retained for a single event's stack trace. */
    static final int MAX_STACK_CHARS = 16 * 1024;

    /**
     * Accepts "2026-06-01 10:12:31.123", "2026-06-01T10:12:31,123" and the
     * Spring Boot 3 offset form "2026-06-01T10:12:31.123+08:00".
     */
    private static final DateTimeFormatter TS_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .optionalStart().appendLiteral('T').optionalEnd()
            .optionalStart().appendLiteral(' ').optionalEnd()
            .appendPattern("HH:mm:ss")
            .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 3, true)
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
            .toFormatter();

    private final LogLineParser parser;

    public LocalFileLogReader(LogLineParser parser) {
        this.parser = parser;
    }

    public List<LogEvent> read(LocalFileLogSource source, EvidenceQuery query) {
        LogLineParser effectiveParser = (source.pattern() == null || source.pattern().isBlank())
                ? parser : new LogLineParser(source.pattern());
        return read(source, effectiveParser, query);
    }

    /**
     * Read using an already-built parser. Callers that resolve a project's source/parser once can use
     * this to avoid recompiling a custom pattern on every query.
     */
    public List<LogEvent> read(LocalFileLogSource source, LogLineParser effectiveParser, EvidenceQuery query) {
        // Min-heap (oldest at head) capped to maxResults so memory stays bounded (review H1).
        int cap = Math.max(1, query.maxResults());
        PriorityQueue<LogEvent> recent =
                new PriorityQueue<>(Comparator.comparing(LogEvent::occurredAt));

        int filesRead = 0;
        for (String pathStr : source.paths()) {
            if (readFile(Path.of(pathStr), source, effectiveParser, query, recent, cap)) {
                filesRead++;
            }
        }

        if (filesRead == 0 && !source.paths().isEmpty()) {
            throw new LogSourceUnavailableException(
                    "No local log file could be read for project " + query.projectId());
        }

        List<LogEvent> events = new ArrayList<>(recent);
        events.sort(Comparator.comparing(LogEvent::occurredAt));
        return events;
    }

    /** @return true if the file was opened and read (even if it yielded no kept events). */
    private boolean readFile(Path path, LocalFileLogSource source, LogLineParser effectiveParser,
                             EvidenceQuery query, PriorityQueue<LogEvent> recent, int cap) {
        try (BufferedReader reader = Files.newBufferedReader(path, source.charset())) {
            long bytesRead = 0;
            Builder current = null;
            String line;
            while ((line = reader.readLine()) != null) {
                bytesRead += line.length() + 1L;
                if (bytesRead > MAX_BYTES_PER_FILE) {
                    log.warn("Local log file {} exceeded {} bytes, truncating scan",
                            path, MAX_BYTES_PER_FILE);
                    break;
                }
                line = LogLineParser.stripAnsi(line); // tolerate colored-console logs
                Optional<LogLineParser.Head> head = effectiveParser.parseHead(line);
                if (head.isPresent()) {
                    offerIfKept(current, source, query, recent, cap);
                    current = new Builder(head.get());
                } else if (current != null) {
                    current.appendContinuation(line);
                }
                // a non-matching line before any head (file preamble) is ignored
            }
            offerIfKept(current, source, query, recent, cap);
            return true;
        } catch (IOException e) {
            log.warn("Failed reading local log file {}: {}", path, e.toString());
            return false;
        }
    }

    private void offerIfKept(Builder builder, LocalFileLogSource source, EvidenceQuery query,
                             PriorityQueue<LogEvent> recent, int cap) {
        if (builder == null || !KEPT_LEVELS.contains(builder.head.level())) {
            return;
        }
        Instant occurredAt = parseTimestamp(builder.head.timestamp(), source);
        if (occurredAt == null || occurredAt.isBefore(query.from()) || occurredAt.isAfter(query.to())) {
            return;
        }
        LogEvent event = builder.toLogEvent(query, occurredAt);
        // Keep only the most recent `cap` events: drop the oldest once over capacity.
        if (recent.size() < cap) {
            recent.offer(event);
        } else if (occurredAt.isAfter(recent.peek().occurredAt())) {
            recent.poll();
            recent.offer(event);
        }
    }

    private Instant parseTimestamp(String ts, LocalFileLogSource source) {
        if (ts == null) {
            return null;
        }
        String normalized = normalizeOffset(ts.replace(',', '.'));
        try {
            TemporalAccessor parsed = TS_FORMAT.parseBest(normalized, OffsetDateTime::from, LocalDateTime::from);
            if (parsed instanceof OffsetDateTime odt) {
                return odt.toInstant();
            }
            return ((LocalDateTime) parsed).atZone(source.zone()).toInstant();
        } catch (DateTimeParseException e) {
            log.debug("Unparseable log timestamp '{}'", ts);
            return null;
        }
    }

    private String normalizeOffset(String timestamp) {
        if (timestamp.matches(".*[+-]\\d{4}$")) {
            return timestamp.substring(0, timestamp.length() - 2)
                    + ":" + timestamp.substring(timestamp.length() - 2);
        }
        return timestamp;
    }

    /** Accumulates a head line plus its (bounded) continuation lines into one event. */
    private static final class Builder {
        private final LogLineParser.Head head;
        private final StringBuilder stack = new StringBuilder();
        private int stackLines;
        private boolean stackTruncated;

        Builder(LogLineParser.Head head) {
            this.head = head;
        }

        void appendContinuation(String line) {
            if (stackTruncated || stackLines >= MAX_STACK_LINES || stack.length() >= MAX_STACK_CHARS) {
                stackTruncated = true;
                return;
            }
            if (stack.length() > 0) {
                stack.append('\n');
            }
            stack.append(line);
            stackLines++;
        }

        LogEvent toLogEvent(EvidenceQuery query, Instant occurredAt) {
            String stackTrace = stack.length() == 0 ? null
                    : (stackTruncated ? stack + "\n…" : stack.toString());
            Map<String, String> attributes = Map.of();
            String exceptionClass = StackTraces.exceptionClass(stackTrace);
            if (exceptionClass != null) {
                attributes = Map.of("exceptionClass", exceptionClass);
            }
            return new LogEvent(query.projectId(), query.environment(), occurredAt,
                    head.level(), head.logger(), null, head.message(), stackTrace, attributes);
        }
    }
}
