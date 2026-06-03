package io.github.atomoty.faultpilot.core.adapter;

import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;

import java.util.List;

/**
 * Queries summarized metric anomalies from a source. See design.md §6.
 */
public interface MetricSourceAdapter {
    List<MetricAnomaly> query(EvidenceQuery query);
}
