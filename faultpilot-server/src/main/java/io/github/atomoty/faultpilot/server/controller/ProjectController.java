package io.github.atomoty.faultpilot.server.controller;

import io.github.atomoty.faultpilot.server.config.FaultPilotProperties;
import io.github.atomoty.faultpilot.server.config.ProjectRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectRegistry projectRegistry;

    public ProjectController(ProjectRegistry projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return projectRegistry.all().stream()
                .map(this::toView)
                .toList();
    }

    private Map<String, Object> toView(FaultPilotProperties.Project p) {
        return Map.of(
                "id", p.getId(),
                "displayName", p.getDisplayName() == null ? p.getId() : p.getDisplayName(),
                "integrationLevel", p.getIntegrationLevel() == null ? "" : p.getIntegrationLevel(),
                "environments", p.getEnvironments());
    }
}
