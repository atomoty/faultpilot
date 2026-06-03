package io.github.atomoty.faultpilot.core.adapter;

import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;

import java.util.List;

/**
 * Queries parameterized slow-SQL summaries from a source. See design.md §6.
 */
public interface SlowSqlSourceAdapter {
    List<SlowSqlSummary> query(EvidenceQuery query);
}
