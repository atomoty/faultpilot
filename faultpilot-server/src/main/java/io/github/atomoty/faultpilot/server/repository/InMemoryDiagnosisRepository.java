package io.github.atomoty.faultpilot.server.repository;

import io.github.atomoty.faultpilot.core.model.DiagnosisReport;

import java.util.Comparator;
import java.util.List;
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

    @Override
    public List<ReportSummary> list(String projectId, String environment, int limit) {
        return reports.values().stream()
                .filter(r -> projectId == null || projectId.isBlank() || projectId.equals(r.projectId()))
                .filter(r -> environment == null || environment.isBlank() || environment.equals(r.environment()))
                .sorted(Comparator.comparing(DiagnosisReport::createdAt).reversed())
                .limit(limit)
                .map(r -> new ReportSummary(r.diagnosisId(), r.projectId(), r.environment(),
                        r.createdAt(), r.summary(), r.ruleFallback()))
                .toList();
    }
}
