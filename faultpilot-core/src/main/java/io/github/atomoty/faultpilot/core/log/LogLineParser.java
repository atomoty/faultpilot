package io.github.atomoty.faultpilot.core.log;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a single log line into its head fields using a named-group regex. Lines that do not match
 * (stack-trace frames, wrapped messages) are treated as continuations of the preceding event.
 *
 * <p>The default pattern matches common Logback lines and Spring Boot's default console layout, e.g.
 * {@code 2026-06-01 10:12:31.123 ERROR [http-1] com.example.Foo - message} or
 * {@code 2026-06-01T10:12:31.123+08:00 ERROR 1234 --- [main] com.example.Foo : message}.
 * Projects may supply a custom pattern with the same named groups: {@code ts}, {@code level},
 * optional {@code thread}, {@code logger}, {@code msg}.
 */
public class LogLineParser {

    /** Default Logback layout. Thread group is optional so layouts without {@code [%thread]} still match. */
    public static final String DEFAULT_PATTERN =
            "^(?<ts>\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}(?:Z|[+-]\\d{2}:?\\d{2})?)\\s+"
                    + "(?<level>TRACE|DEBUG|INFO|WARN|ERROR)\\s+"
                    + "(?:\\d+\\s+)?"
                    + "(?:---\\s+)?"
                    + "(?:\\[(?<thread>[^\\]]+)\\]\\s+)?"
                    + "(?<logger>[\\w$.-]+)\\s*[-:]\\s*"
                    + "(?<msg>.*)$";

    /** CSI ANSI escape sequences, e.g. the color codes emitted by Logback's colored console layout. */
    private static final Pattern ANSI = Pattern.compile("\\e\\[[0-9;]*m");

    private final Pattern pattern;
    private final boolean hasThreadGroup;

    public LogLineParser() {
        this(DEFAULT_PATTERN);
    }

    public LogLineParser(String regex) {
        this.pattern = Pattern.compile(regex);
        this.hasThreadGroup = regex.contains("<thread>");
    }

    /** Remove ANSI escape sequences (colored-console output) so patterns match the plain text. */
    public static String stripAnsi(String line) {
        if (line == null || line.indexOf('') < 0) {
            return line;
        }
        return ANSI.matcher(line).replaceAll("");
    }

    /** The parsed head fields of a log line. */
    public record Head(String timestamp, String level, String thread, String logger, String message) {
    }

    /**
     * Try to parse {@code line} as the head of a new log event. Empty when the line does not match
     * (i.e. it is a continuation of the previous event).
     */
    public Optional<Head> parseHead(String line) {
        Matcher m = pattern.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        String thread = hasThreadGroup ? group(m, "thread") : null;
        return Optional.of(new Head(
                group(m, "ts"), group(m, "level"), thread, group(m, "logger"), group(m, "msg")));
    }

    private static String group(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException noSuchGroup) {
            return null;
        }
    }
}
