package io.github.atomoty.faultpilot.server.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.core.model.DiagnosisReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistent {@link DiagnosisReportRepository} backed by the internal H2 store. The full report is
 * stored as JSON in {@code report_json}; {@code project_id/environment/created_at} are extracted as
 * columns for querying.
 */
@Repository
public class JdbcDiagnosisRepository implements DiagnosisReportRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcDiagnosisRepository.class);

    private static final String UPSERT = """
            MERGE INTO diagnosis_report (id, project_id, environment, created_at, report_json)
            KEY (id) VALUES (?, ?, ?, ?, ?)""";
    private static final String FIND = "SELECT report_json FROM diagnosis_report WHERE id = ?";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcDiagnosisRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void save(DiagnosisReport report) {
        jdbc.update(UPSERT, report.diagnosisId(), report.projectId(), report.environment(),
                Timestamp.from(report.createdAt()), toJson(report));
    }

    @Override
    public Optional<DiagnosisReport> find(String diagnosisId) {
        try {
            String json = jdbc.queryForObject(FIND, String.class, diagnosisId);
            return Optional.of(fromJson(json));
        } catch (EmptyResultDataAccessException notFound) {
            return Optional.empty();
        }
    }

    @Override
    public List<ReportSummary> list(String projectId, String environment, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, created_at, report_json FROM diagnosis_report WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (projectId != null && !projectId.isBlank()) {
            sql.append(" AND project_id = ?");
            args.add(projectId);
        }
        if (environment != null && !environment.isBlank()) {
            sql.append(" AND environment = ?");
            args.add(environment);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(limit);

        // A single corrupt row must not take the whole history listing down: skip it with a WARN.
        return jdbc.query(sql.toString(), (rs, rowNum) -> {
            String id = rs.getString("id");
            try {
                DiagnosisReport report = fromJson(rs.getString("report_json"));
                return new ReportSummary(report.diagnosisId(), report.projectId(), report.environment(),
                        rs.getTimestamp("created_at").toInstant(), report.summary(), report.ruleFallback());
            } catch (IllegalStateException corrupt) {
                log.warn("Skipping unreadable stored report {} in history listing: {}",
                        id, corrupt.getMessage());
                return null;
            }
        }, args.toArray()).stream().filter(Objects::nonNull).toList();
    }

    private String toJson(DiagnosisReport report) {
        try {
            return mapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize diagnosis report " + report.diagnosisId(), e);
        }
    }

    private DiagnosisReport fromJson(String json) {
        try {
            return mapper.readValue(json, DiagnosisReport.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize diagnosis report", e);
        }
    }
}
