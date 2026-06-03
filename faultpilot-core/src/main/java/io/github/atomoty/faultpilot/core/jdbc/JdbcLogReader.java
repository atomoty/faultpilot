package io.github.atomoty.faultpilot.core.jdbc;

import io.github.atomoty.faultpilot.core.log.LogSourceUnavailableException;
import io.github.atomoty.faultpilot.core.log.StackTraces;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads project logs from a database view exposing the canonical columns
 * {@code occurred_at, level, trace_id, message, stack_trace} (design §4.4). The SQL is fixed and
 * code-built; only the time range and row cap vary, all bound as parameters. The view name is the
 * sole project-controlled identifier and is validated against an allowlist before interpolation.
 *
 * <p>The connection is read-only with connect/query timeouts. A connection or query failure throws
 * {@link LogSourceUnavailableException} so the collector marks the source unavailable (spec §9).
 */
public class JdbcLogReader {

    private static final Logger log = LoggerFactory.getLogger(JdbcLogReader.class);

    /** A single SQL identifier segment: letter/underscore start, then word chars, max 64. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Set<String> KEPT_LEVELS = Set.of("WARN", "ERROR");

    public List<LogEvent> read(JdbcLogSource source, EvidenceQuery query) {
        String view = validatedView(source.view());
        String sql = "SELECT occurred_at, level, trace_id, message, stack_trace FROM " + view
                + " WHERE occurred_at >= ? AND occurred_at <= ?"
                + " AND UPPER(level) IN ('WARN', 'ERROR')"
                + " ORDER BY occurred_at DESC";

        Properties props = new Properties();
        if (source.username() != null) {
            props.setProperty("user", source.username());
        }
        if (source.password() != null) {
            props.setProperty("password", source.password());
        }
        DriverManager.setLoginTimeout(Math.max(1, source.connectTimeoutMs() / 1000));

        try (Connection conn = DriverManager.getConnection(source.url(), props)) {
            conn.setReadOnly(true);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(Math.max(1, source.queryTimeoutMs() / 1000));
                ps.setMaxRows(Math.max(1, query.maxResults()));
                ps.setTimestamp(1, toTimestamp(query.from(), source));
                ps.setTimestamp(2, toTimestamp(query.to(), source));
                try (ResultSet rs = ps.executeQuery()) {
                    return mapRows(rs, source, query);
                }
            }
        } catch (SQLException e) {
            log.warn("JDBC log source unavailable for project {} ({}): {}",
                    query.projectId(), JdbcUrls.safe(source.url()), e.toString());
            throw new LogSourceUnavailableException(
                    "JDBC log source unavailable for project " + query.projectId());
        }
    }

    /** Validate "view" or "schema.view"; reject anything that is not a plain identifier. */
    static String validatedView(String view) {
        if (view == null || view.isBlank()) {
            throw new IllegalArgumentException("logs.view must be configured for type=jdbc");
        }
        String[] parts = view.split("\\.", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("Invalid logs.view (too many qualifiers): " + view);
        }
        for (String part : parts) {
            if (!IDENTIFIER.matcher(part).matches()) {
                throw new IllegalArgumentException("Invalid logs.view identifier: " + view);
            }
        }
        return view;
    }

    private List<LogEvent> mapRows(ResultSet rs, JdbcLogSource source, EvidenceQuery query) throws SQLException {
        List<LogEvent> events = new ArrayList<>();
        while (rs.next()) {
            Instant occurredAt = toInstant(rs.getTimestamp("occurred_at"), source);
            if (occurredAt == null) {
                continue;
            }
            String level = rs.getString("level");
            if (level == null || !KEPT_LEVELS.contains(level.toUpperCase())) {
                continue;
            }
            String traceId = rs.getString("trace_id");
            String message = rs.getString("message");
            String stackTrace = rs.getString("stack_trace");

            Map<String, String> attributes = Map.of();
            String exceptionClass = StackTraces.exceptionClass(stackTrace);
            if (exceptionClass != null) {
                attributes = Map.of("exceptionClass", exceptionClass);
            }
            events.add(new LogEvent(query.projectId(), query.environment(), occurredAt,
                    level, null, traceId, message, stackTrace, attributes));
        }
        return events;
    }

    /** Treat the stored TIMESTAMP (no offset) as wall-clock time in the configured zone. */
    private Timestamp toTimestamp(Instant instant, JdbcLogSource source) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(instant, source.zone()));
    }

    private Instant toInstant(Timestamp ts, JdbcLogSource source) {
        return ts == null ? null : ts.toLocalDateTime().atZone(source.zone()).toInstant();
    }
}
