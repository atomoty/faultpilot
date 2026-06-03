package io.github.atomoty.faultpilot.server.security;

import io.github.atomoty.faultpilot.server.config.FaultPilotProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Authorizes event ingestion (review #1, SSOT §13, partial). By default a Bearer token bound to the
 * target project and environment is required. Rate limiting, idempotency and replay-window checks
 * are deferred to the {@code events:batch} protocol in a later round.
 */
@Service
public class IngestionAuthService {

    private static final Logger log = LoggerFactory.getLogger(IngestionAuthService.class);
    private static final String BEARER = "Bearer ";

    private final FaultPilotProperties.Ingestion ingestion;

    public IngestionAuthService(FaultPilotProperties properties) {
        this.ingestion = properties.getIngestion();
    }

    @PostConstruct
    void warnIfOpen() {
        if (!ingestion.isRequireToken()) {
            log.warn("============================================================");
            log.warn("FaultPilot ingestion is OPEN: faultpilot.ingestion.require-token=false.");
            log.warn("POST /api/v1/events accepts anonymous writes. Use this only for local demos;");
            log.warn("never run an online deployment in this mode.");
            log.warn("============================================================");
        }
    }

    /**
     * Validate the Authorization header for a write to {@code projectId}/{@code environment}.
     *
     * @throws MissingTokenException when a token is required but absent/malformed (HTTP 401)
     * @throws ForbiddenTokenException when the token is unknown or not bound to this project/env (HTTP 403)
     */
    public void authorize(String authorizationHeader, String projectId, String environment) {
        // Open mode (local demos only): explicitly opted out of token enforcement.
        if (!ingestion.isRequireToken()) {
            return;
        }

        List<FaultPilotProperties.IngestionToken> tokens = ingestion.getTokens();
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER)) {
            throw new MissingTokenException("缺少 Bearer Token");
        }
        String token = authorizationHeader.substring(BEARER.length()).trim();

        boolean bound = tokens.stream().anyMatch(t ->
                t.getToken() != null
                        && t.getToken().equals(token)
                        && projectId.equals(t.getProjectId())
                        && t.getEnvironments().contains(environment));
        if (!bound) {
            throw new ForbiddenTokenException("Token 无效或未绑定该项目/环境");
        }
    }

    /** Maps to HTTP 401. */
    public static class MissingTokenException extends RuntimeException {
        public MissingTokenException(String message) {
            super(message);
        }
    }

    /** Maps to HTTP 403. */
    public static class ForbiddenTokenException extends RuntimeException {
        public ForbiddenTokenException(String message) {
            super(message);
        }
    }
}
