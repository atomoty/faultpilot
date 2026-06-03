package io.github.atomoty.faultpilot.adapters.mock;

import io.github.atomoty.faultpilot.core.adapter.MetricSourceAdapter;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;

import java.util.List;

/** Returns the demo scenario's metric anomalies for the queried project/environment. */
public class MockMetricSourceAdapter implements MetricSourceAdapter {

    @Override
    public List<MetricAnomaly> query(EvidenceQuery query) {
        return DemoFixtures.find(query.projectId(), query.environment())
                .map(s -> s.metrics().stream()
                        .filter(m -> inRange(m.observedAt(), query))
                        .limit(query.maxResults())
                        .toList())
                .orElse(List.of());
    }

    private boolean inRange(java.time.Instant at, EvidenceQuery q) {
        return at != null && !at.isBefore(q.from()) && !at.isAfter(q.to());
    }
}
