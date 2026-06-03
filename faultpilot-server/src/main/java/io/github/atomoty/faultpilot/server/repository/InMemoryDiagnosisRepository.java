package io.github.atomoty.faultpilot.server.repository;

import io.github.atomoty.faultpilot.core.model.DiagnosisReport;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of generated reports keyed by diagnosisId. v0.1.0 only — replaced by H2/JDBC
 * persistence in a later round (specification.md §14).
 */
@Repository
public class InMemoryDiagnosisRepository {

    private final Map<String, DiagnosisReport> reports = new ConcurrentHashMap<>();

    public void save(DiagnosisReport report) {
        reports.put(report.diagnosisId(), report);
    }

    public Optional<DiagnosisReport> find(String diagnosisId) {
        return Optional.ofNullable(reports.get(diagnosisId));
    }
}
