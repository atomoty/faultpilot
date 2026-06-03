package io.github.atomoty.faultpilot.adapters.mock;

import io.github.atomoty.faultpilot.core.adapter.ChangeEventSource;
import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;

import java.util.List;

/**
 * Returns the demo scenario's change events so the deployment-regression demo is self-contained
 * (no prior POST /api/v1/events required).
 */
public class MockChangeEventSource implements ChangeEventSource {

    @Override
    public List<ChangeEvent> query(EvidenceQuery query) {
        return DemoFixtures.find(query.projectId(), query.environment())
                .map(s -> s.changeEvents().stream()
                        .filter(e -> inRange(e.occurredAt(), query))
                        .toList())
                .orElse(List.of());
    }

    private boolean inRange(java.time.Instant at, EvidenceQuery q) {
        return at != null && !at.isBefore(q.from()) && !at.isAfter(q.to());
    }
}
