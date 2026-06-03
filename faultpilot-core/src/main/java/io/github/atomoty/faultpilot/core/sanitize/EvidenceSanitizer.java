package io.github.atomoty.faultpilot.core.sanitize;

import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Best-effort redaction of secrets and PII before evidence leaves for the model.
 * See specification.md §12: this is best-effort, not a zero-leak guarantee.
 *
 * <p>Also exposes normalization helpers used by clustering (UUIDs, numeric ids, timestamps, IPs,
 * emails and long hex strings are replaced with placeholders — specification.md §7.4).
 */
public class EvidenceSanitizer {

    private static final String MASK = "***";

    /** Max characters kept for a single log sample (specification.md §8.1). */
    public static final int MAX_SAMPLE_CHARS = 1200;
    /** Max stack-trace lines kept for a single sample (specification.md §8.1). */
    public static final int MAX_STACK_LINES = 12;

    // Secret/PII patterns. Order matters: more specific before generic.
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            // Bearer <token> (with or without a leading "Authorization:"), including the token itself.
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*)?bearer\\s+[^\\s\",;)]+"),
            // password=xxx / "token": "xxx" / apiKey: xxx
            Pattern.compile("(?i)(password|passwd|pwd|token|secret|api[_-]?key|authorization)\\s*[:=]\\s*\"?[^\\s\",;)]+\"?"),
            // emails
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            // CN mainland mobile numbers
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
            // CN id card (18 digits, optional trailing X)
            Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)")
    );

    private static final Pattern UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern LONG_HEX = Pattern.compile("\\b[0-9a-fA-F]{16,}\\b");
    private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b");
    private static final Pattern ISO_TIMESTAMP =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+\\b");

    // Attribute key names whose values must be masked outright.
    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i)(pass(word|wd)?|pwd|token|secret|api[_-]?key|authorization|credential|access[_-]?key)");

    /** Redact a single text fragment. Returns null when input is null. */
    public String redact(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        for (Pattern p : SECRET_PATTERNS) {
            result = p.matcher(result).replaceAll(MASK);
        }
        return result;
    }

    /**
     * Produce a redacted copy of a log event: message, stack trace and every attribute value are
     * scrubbed; the sample is also truncated to the size/line caps (specification.md §8.1, §12).
     */
    public LogEvent sanitize(LogEvent event) {
        return new LogEvent(
                event.projectId(),
                event.environment(),
                event.occurredAt(),
                event.level(),
                event.logger(),
                event.traceId(),
                truncate(redact(event.message()), MAX_SAMPLE_CHARS),
                limitStackLines(redact(event.stackTrace())),
                redactValues(event.attributes())
        );
    }

    /** Produce a redacted copy of a change event (attribute values scrubbed). */
    public ChangeEvent sanitize(ChangeEvent event) {
        return new ChangeEvent(event.evidenceId(), event.projectId(), event.environment(),
                event.type(), event.occurredAt(), redactValues(event.attributes()));
    }

    /** Produce a redacted copy of a slow-SQL summary (template scrubbed of any stray literals). */
    public SlowSqlSummary sanitize(SlowSqlSummary sql) {
        return new SlowSqlSummary(sql.evidenceId(), redact(sql.sqlTemplate()), sql.occurrences(),
                sql.avgDurationMs(), sql.maxDurationMs(), sql.firstSeen(), sql.source());
    }

    /** Produce a redacted copy of a metric anomaly (description scrubbed). */
    public MetricAnomaly sanitize(MetricAnomaly metric) {
        return new MetricAnomaly(metric.evidenceId(), metric.metric(), redact(metric.description()),
                metric.baselineValue(), metric.currentValue(), metric.unit(), metric.observedAt());
    }

    /**
     * Redact every value in a map, preserving keys and order. A value is fully masked when its key
     * name looks sensitive (e.g. {@code password}, {@code token}, {@code apiKey}); otherwise the
     * value is scrubbed for inline secrets/PII.
     */
    public Map<String, String> redactValues(Map<String, String> in) {
        if (in == null || in.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        in.forEach((k, v) -> out.put(k, isSensitiveKey(k) ? MASK : redact(v)));
        return out;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        return SENSITIVE_KEY.matcher(key).find();
    }

    /** Keep at most {@link #MAX_STACK_LINES} lines of a stack trace, marking the cut. */
    public String limitStackLines(String stackTrace) {
        if (stackTrace == null) {
            return null;
        }
        String[] lines = stackTrace.split("\\R");
        if (lines.length <= MAX_STACK_LINES) {
            return stackTrace;
        }
        return String.join("\n", java.util.Arrays.copyOf(lines, MAX_STACK_LINES)) + "\n…";
    }

    /** Truncate to a maximum length, appending an ellipsis marker when cut. */
    public String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "…";
    }

    /**
     * Normalize a message into a clustering template by masking variable tokens
     * (UUIDs, timestamps, IPs, emails, long hex, then bare numbers). See specification.md §7.4.
     */
    public String normalizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String result = redact(message);
        result = UUID.matcher(result).replaceAll("{uuid}");
        result = ISO_TIMESTAMP.matcher(result).replaceAll("{ts}");
        result = IPV4.matcher(result).replaceAll("{ip}");
        result = LONG_HEX.matcher(result).replaceAll("{hex}");
        result = NUMBER.matcher(result).replaceAll("{n}");
        return result.trim();
    }

    /**
     * Extract a normalized top stack frame (first "at ..." line, line numbers stripped).
     * Returns null when there is no stack trace.
     */
    public String topStackFrame(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return null;
        }
        for (String raw : stackTrace.split("\\R")) {
            String line = raw.trim();
            if (line.startsWith("at ")) {
                return line.substring(3).replaceAll(":\\d+\\)", ")").trim();
            }
        }
        return null;
    }
}
