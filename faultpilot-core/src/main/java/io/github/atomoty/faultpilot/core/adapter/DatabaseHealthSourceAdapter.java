package io.github.atomoty.faultpilot.core.adapter;

import io.github.atomoty.faultpilot.core.model.DatabaseHealthSnapshot;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;

/**
 * Queries a read-only snapshot of database health (connections, long transactions, lock waits).
 * See design.md §6, §9. Implementations use fixed, code-built SQL only; the model never generates SQL.
 */
public interface DatabaseHealthSourceAdapter {

    /**
     * Produce a health snapshot. A total connection failure should surface as a
     * {@link io.github.atomoty.faultpilot.core.jdbc.DataSourceUnavailableException}; an individual
     * sub-query the account cannot run (e.g. lock waits) should be skipped, leaving the rest of the
     * snapshot {@code available} (spec §9.3).
     */
    DatabaseHealthSnapshot query(EvidenceQuery query);
}
