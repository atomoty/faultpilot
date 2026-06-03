package io.github.atomoty.faultpilot.server.repository;

import io.github.atomoty.faultpilot.core.adapter.ChangeEventSource;
import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory store for change events written via {@code POST /api/v1/events}. Also acts as a
 * {@link ChangeEventSource} so written events participate in correlation. v0.1.0 only — replaced
 * by persistent storage in a later round.
 */
@Repository
public class InMemoryEventStore implements ChangeEventSource {

    private final List<ChangeEvent> events = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    /** Store an event, assigning an evidence id. Returns the stored event. */
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
