package io.github.atomoty.faultpilot.core.adapter;

import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.LogEvent;

import java.util.List;

/**
 * Queries normalized log events from a source (mock, local file, JDBC, log platform...).
 * See design.md §6.
 */
public interface LogSourceAdapter {
    List<LogEvent> query(EvidenceQuery query);
}
