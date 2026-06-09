package io.github.atomoty.faultpilot.server.controller;

import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.sanitize.EvidenceSanitizer;
import io.github.atomoty.faultpilot.server.api.EventRequestDto;
import io.github.atomoty.faultpilot.server.config.ProjectRegistry;
import io.github.atomoty.faultpilot.server.repository.EventStore;
import io.github.atomoty.faultpilot.server.security.IngestionAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventStore eventStore;
    private final ProjectRegistry projectRegistry;
    private final IngestionAuthService ingestionAuth;
    private final EvidenceSanitizer sanitizer;

    public EventController(EventStore eventStore, ProjectRegistry projectRegistry,
                          IngestionAuthService ingestionAuth, EvidenceSanitizer sanitizer) {
        this.eventStore = eventStore;
        this.projectRegistry = projectRegistry;
        this.ingestionAuth = ingestionAuth;
        this.sanitizer = sanitizer;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> write(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody EventRequestDto dto) {
        ingestionAuth.authorize(authorization, dto.projectId(), dto.environment());
        projectRegistry.require(dto.projectId(), dto.environment());
        // Sanitize at write time so raw secrets never rest in the event store (review #2).
        ChangeEvent sanitized = sanitizer.sanitize(new ChangeEvent(
                null, dto.projectId(), dto.environment(), dto.type(), dto.occurredAt(),
                dto.attributes() == null ? Map.of() : dto.attributes()));
        ChangeEvent stored = eventStore.save(sanitized);
        return Map.of("evidenceId", stored.evidenceId());
    }
}
