package io.github.atomoty.faultpilot.adapters.mock;

import io.github.atomoty.faultpilot.core.adapter.SlowSqlSourceAdapter;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;

import java.util.List;

/** Returns the demo scenario's slow-SQL summaries for the queried project/environment. */
public class MockSlowSqlSourceAdapter implements SlowSqlSourceAdapter {

    @Override
    public List<SlowSqlSummary> query(EvidenceQuery query) {
        return DemoFixtures.find(query.projectId(), query.environment())
                .map(s -> s.slowSql().stream()
                        .filter(sql -> inRange(sql.firstSeen(), query))
                        .limit(query.maxResults())
                        .toList())
                .orElse(List.of());
    }

    private boolean inRange(java.time.Instant at, EvidenceQuery q) {
        return at != null && !at.isBefore(q.from()) && !at.isAfter(q.to());
    }
}
