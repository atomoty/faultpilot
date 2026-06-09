package io.github.atomoty.faultpilot.server.repository;

import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory {@link EventStore}. Not a Spring bean — the persistent {@link JdbcEventStore} is the
 * registered implementation; this is kept for tests.
 */
public class InMemoryEventStore implements EventStore {

    private final List<ChangeEvent> events = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public ChangeEvent save(ChangeEvent event) {
        String evidenceId = "event-" + sequence.incrementAndGet();
        ChangeEvent stored = new ChangeEvent(evidenceId, event.projectId(), event.environment(),
                event.type(), event.occurredAt(), event.attributes());
        events.add(stored);
        return stored;
    }

    @Override
    public List<ChangeEvent> query(EvidenceQuery query) {
        return events.stream()
                .filter(e -> e.projectId().equals(query.projectId()))
                .filter(e -> e.environment().equals(query.environment()))
                .filter(e -> e.occurredAt() != null
                        && !e.occurredAt().isBefore(query.from())
                        && !e.occurredAt().isAfter(query.to()))
                .toList();
    }
}
