package io.github.atomoty.faultpilot.core.log;

import java.util.regex.Pattern;

/**
 * Helpers for deriving structured fields from a stack trace. Shared by log source adapters
 * (local file, JDBC) so the clustering key stays consistent across sources.
 */
public final class StackTraces {

    /** A fully-qualified throwable token, e.g. {@code java.lang.NullPointerException}. */
    private static final Pattern THROWABLE =
            Pattern.compile("\\b([\\w$.]+(?:Exception|Error))\\b");

    private StackTraces() {
    }

    /**
     * Derive a simple exception class name from a stack trace only (never from free-text messages:
     * a message merely mentioning "…Exception" must not be treated as a thrown exception).
     *
     * @return the simple class name of the first throwable token, or null if none / no stack trace
     */
    public static String exceptionClass(String stackTrace) {
        if (stackTrace == null) {
            return null;
        }
        var m = THROWABLE.matcher(stackTrace);
        if (!m.find()) {
            return null;
        }
        String fqcn = m.group(1);
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
    }
}
