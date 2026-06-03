package io.github.atomoty.faultpilot.core.model;

import java.util.List;

/**
 * A read-only snapshot of database health. See development-plan.md §5.
 * Not produced in the v0.1.0 mock round, but part of the evidence contract.
 *
 * @param available        whether the database health source could be queried at all
 * @param unavailableReason populated when {@code available} is false
 */
public record DatabaseHealthSnapshot(
        String evidenceId,
        boolean available,
        String unavailableReason,
        int activeConnections,
        int idleConnections,
        int waitingConnections,
        List<String> longTransactions,
        List<String> lockWaits
) {
    public static final String NO_DATABASE_CONFIGURED = "no database configured";

    public static DatabaseHealthSnapshot unavailable(String reason) {
        return new DatabaseHealthSnapshot(null, false, reason, 0, 0, 0, List.of(), List.of());
    }

    public static DatabaseHealthSnapshot withoutDatabaseConfig() {
        return unavailable(NO_DATABASE_CONFIGURED);
    }

    public boolean isNoDatabaseConfigured() {
        return NO_DATABASE_CONFIGURED.equals(unavailableReason);
    }
}
