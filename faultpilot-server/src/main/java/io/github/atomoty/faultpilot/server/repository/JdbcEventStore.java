package io.github.atomoty.faultpilot.server.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persistent {@link EventStore} backed by the internal H2 store. Attributes are stored as JSON in
 * {@code attributes_json}; evidence ids come from a DB sequence so they stay unique across restarts.
 */
@Repository
public class JdbcEventStore implements EventStore {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String NEXT_ID = "SELECT NEXT VALUE FOR change_event_seq";
    private static final String INSERT = """
            INSERT INTO change_event (evidence_id, project_id, environment, type, occurred_at, attributes_json)
            VALUES (?, ?, ?, ?, ?, ?)""";
    private static final String QUERY = """
            SELECT evidence_id, project_id, environment, type, occurred_at, attributes_json
            FROM (
                SELECT evidence_id, project_id, environment, type, occurred_at, attributes_json
                FROM change_event
                WHERE project_id = ? AND environment = ? AND occurred_at BETWEEN ? AND ?
                ORDER BY occurred_at DESC
                LIMIT ?
            )
            ORDER BY occurred_at""";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcEventStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public ChangeEvent save(ChangeEvent event) {
        long seq = jdbc.queryForObject(NEXT_ID, Long.class);
        String evidenceId = "event-" + seq;
        ChangeEvent stored = new ChangeEvent(evidenceId, event.projectId(), event.environment(),
                event.type(), event.occurredAt(), event.attributes());
        Timestamp occurredAt = stored.occurredAt() == null ? null : Timestamp.from(stored.occurredAt());
        jdbc.update(INSERT, evidenceId, stored.projectId(), stored.environment(), stored.type(),
                occurredAt, toJson(stored.attributes()));
        return stored;
    }

    @Override
    public List<ChangeEvent> query(EvidenceQuery query) {
        return jdbc.query(QUERY, eventRowMapper(),
                query.projectId(), query.environment(),
                Timestamp.from(query.from()), Timestamp.from(query.to()), query.maxResults());
    }

    private RowMapper<ChangeEvent> eventRowMapper() {
        return (rs, rowNum) -> {
            Timestamp occurredAt = rs.getTimestamp("occurred_at");
            Instant instant = occurredAt == null ? null : occurredAt.toInstant();
            return new ChangeEvent(
                    rs.getString("evidence_id"), rs.getString("project_id"), rs.getString("environment"),
                    rs.getString("type"), instant, fromJson(rs.getString("attributes_json")));
        };
    }

    private String toJson(Map<String, String> attributes) {
        try {
            return mapper.writeValueAsString(attributes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event attributes", e);
        }
    }

    private Map<String, String> fromJson(String json) {
        try {
            return mapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize event attributes", e);
        }
    }
}
