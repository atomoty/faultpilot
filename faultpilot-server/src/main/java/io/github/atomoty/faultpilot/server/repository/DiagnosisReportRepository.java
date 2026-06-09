package io.github.atomoty.faultpilot.server.repository;

import io.github.atomoty.faultpilot.core.model.DiagnosisReport;

import java.util.Optional;

/**
 * Stores generated diagnosis reports keyed by diagnosisId. Implementations may be in-memory
 * (tests) or persistent (JDBC); callers depend only on this interface.
 */
public interface DiagnosisReportRepository {

    void save(DiagnosisReport report);

    Optional<DiagnosisReport> find(String diagnosisId);
}
