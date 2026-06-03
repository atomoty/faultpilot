package io.github.atomoty.faultpilot.adapters.mock;

import io.github.atomoty.faultpilot.core.adapter.LogSourceAdapter;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.LogEvent;

import java.util.List;

/**
 * Returns the demo scenario's log events for the queried project/environment, filtered by time range
 * and capped by {@code maxResults}.
 */
public class MockLogSourceAdapter implements LogSourceAdapter {

    @Override
    public List<LogEvent> query(EvidenceQuery query) {
        return DemoFixtures.find(query.projectId(), query.environment())
                .map(s -> s.logs().stream()
                        .filter(e -> inRange(e.occurredAt(), query))
                        .limit(query.maxResults())
                        .toList())
                .orElse(List.of());
    }

    private boolean inRange(java.time.Instant at, EvidenceQuery q) {
        return at != null && !at.isBefore(q.from()) && !at.isAfter(q.to());
    }
}
