package io.github.atomoty.faultpilot.server.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Loads registered projects from configuration and validates project/environment lookups.
 * See design.md §5 (ProjectRegistry), specification.md §4.
 */
@Component
public class ProjectRegistry {

    private final List<FaultPilotProperties.Project> projects;

    public ProjectRegistry(FaultPilotProperties properties) {
        this.projects = properties.getProjects();
    }

    public List<FaultPilotProperties.Project> all() {
        return projects;
    }

    public Optional<FaultPilotProperties.Project> find(String projectId) {
        return projects.stream().filter(p -> p.getId().equals(projectId)).findFirst();
    }

    /** Resolve a project, requiring the environment to be registered. */
    public FaultPilotProperties.Project require(String projectId, String environment) {
        FaultPilotProperties.Project project = find(projectId)
                .orElseThrow(() -> new UnknownProjectException("未注册的项目: " + projectId));
        if (!project.getEnvironments().contains(environment)) {
            throw new UnknownProjectException(
                    "项目 " + projectId + " 未注册环境: " + environment);
        }
        return project;
    }

    /** Thrown when a project or environment is not registered. Maps to HTTP 404. */
    public static class UnknownProjectException extends RuntimeException {
        public UnknownProjectException(String message) {
            super(message);
        }
    }
}
