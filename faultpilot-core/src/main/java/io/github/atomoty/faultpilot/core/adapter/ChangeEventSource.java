package io.github.atomoty.faultpilot.core.adapter;

import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;

import java.util.List;

/**
 * Supplies change/lifecycle events (deployment, rollback, job events) for correlation.
 * Backed by the event store. See design.md §3.4, §12.
 */
public interface ChangeEventSource {
    List<ChangeEvent> query(EvidenceQuery query);
}
