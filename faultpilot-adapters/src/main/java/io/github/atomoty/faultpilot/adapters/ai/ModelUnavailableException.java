package io.github.atomoty.faultpilot.adapters.ai;

/**
 * Thrown when a real model provider cannot produce a usable result (transport error, timeout,
 * non-success status, empty or malformed output). {@code DiagnosisService} catches it and falls
 * back to a rule-only report. Messages must never include secrets (API keys, raw payloads).
 */
public class ModelUnavailableException extends RuntimeException {
    public ModelUnavailableException(String message) {
        super(message);
    }

    public ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
