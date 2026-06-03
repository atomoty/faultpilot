package io.github.atomoty.faultpilot.core.log;

/**
 * Thrown by a log source when it could not be read at all (e.g. every configured file is
 * missing or unreadable). It signals genuine unavailability so the collector marks the source
 * unavailable rather than reporting a misleading empty result (review H2, spec §9).
 */
public class LogSourceUnavailableException extends RuntimeException {
    public LogSourceUnavailableException(String message) {
        super(message);
    }
}
