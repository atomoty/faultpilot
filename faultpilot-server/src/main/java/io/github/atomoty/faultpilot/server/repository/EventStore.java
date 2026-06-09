package io.github.atomoty.faultpilot.server.repository;

import io.github.atomoty.faultpilot.core.adapter.ChangeEventSource;
import io.github.atomoty.faultpilot.core.model.ChangeEvent;

/**
 * Stores change events written via {@code POST /api/v1/events}, assigning each an evidence id.
 * Also a {@link ChangeEventSource} so stored events participate in correlation during a diagnosis.
 * Implementations may be in-memory (tests) or persistent (JDBC); callers depend only on this interface.
 */
public interface EventStore extends ChangeEventSource {

    /** Store an event, assigning an evidence id. Returns the stored event. */
    ChangeEvent save(ChangeEvent event);
}
