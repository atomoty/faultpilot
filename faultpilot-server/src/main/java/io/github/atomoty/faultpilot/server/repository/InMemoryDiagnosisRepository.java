package io.github.atomoty.faultpilot.server.repository;

import io.github.atomoty.faultpilot.core.model.DiagnosisReport;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link DiagnosisReportRepository}. Not a Spring bean — the persistent
 * {@link JdbcDiagnosisRepository} is the registered implementation; this is kept for tests.
 */
public class InMemoryDiagnosisRepository implements DiagnosisReportRepository {

    private final Map<String, DiagnosisReport> reports = new ConcurrentHashMap<>();

    @Override
    public void save(DiagnosisReport report) {
        reports.put(report.diagnosisId(), report);
    }

    @Override
    public Optional<DiagnosisReport> find(String diagnosisId) {
        return Optional.ofNullable(reports.get(diagnosisId));
    }
}
