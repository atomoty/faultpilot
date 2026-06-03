package io.github.atomoty.faultpilot.server.adapter;

import io.github.atomoty.faultpilot.core.adapter.DatabaseHealthSourceAdapter;
import io.github.atomoty.faultpilot.core.adapter.SlowSqlSourceAdapter;
import io.github.atomoty.faultpilot.core.jdbc.DataSourceConfig;
import io.github.atomoty.faultpilot.core.jdbc.db.MysqlDatabaseHealthSource;
import io.github.atomoty.faultpilot.core.jdbc.db.MysqlSlowSqlSource;
import io.github.atomoty.faultpilot.core.jdbc.db.PostgresDatabaseHealthSource;
import io.github.atomoty.faultpilot.core.jdbc.db.PostgresSlowSqlSource;
import io.github.atomoty.faultpilot.core.model.DatabaseHealthSnapshot;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;
import io.github.atomoty.faultpilot.core.sanitize.EvidenceSanitizer;
import io.github.atomoty.faultpilot.server.config.FaultPilotProperties;
import io.github.atomoty.faultpilot.server.config.ProjectRegistry;
import org.springframework.boot.convert.DurationStyle;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a project's read-only database sources (health + slow SQL) by {@code database.type}
 * (mysql/postgres), building and caching dialect implementations once per project. Projects without
 * a database block contribute nothing.
 *
 * <p>Exposes the two SPIs via {@link #healthAdapter()} and {@link #slowSqlAdapter()} rather than
 * implementing both directly (their {@code query(EvidenceQuery)} methods would clash on return type).
 */
public class DatabaseSourceRouter {

    private final ProjectRegistry projectRegistry;
    private final EvidenceSanitizer sanitizer;
    private final ConcurrentHashMap<String, Resolved> cache = new ConcurrentHashMap<>();

    public DatabaseSourceRouter(ProjectRegistry projectRegistry, EvidenceSanitizer sanitizer) {
        this.projectRegistry = projectRegistry;
        this.sanitizer = sanitizer;
    }

    /** A health adapter that routes per project; emits a sentinel snapshot when no DB is set. */
    public DatabaseHealthSourceAdapter healthAdapter() {
        return query -> resolved(query.projectId())
                .map(r -> r.health().query(query))
                .orElse(DatabaseHealthSnapshot.withoutDatabaseConfig());
    }

    /** A slow-SQL adapter that routes per project; returns empty when no DB is set. */
    public SlowSqlSourceAdapter slowSqlAdapter() {
        return query -> resolved(query.projectId())
                .map(r -> r.slowSql().query(query))
                .orElse(List.<SlowSqlSummary>of());
    }

    private Optional<Resolved> resolved(String projectId) {
        FaultPilotProperties.DatabaseConfig db = projectRegistry.find(projectId)
                .map(FaultPilotProperties.Project::getDatabase)
                .filter(FaultPilotProperties.DatabaseConfig::isEnabled)
                .orElse(null);
        if (db == null) {
            return Optional.empty();
        }
        return Optional.of(cache.computeIfAbsent(projectId, id -> build(db)));
    }

    private Resolved build(FaultPilotProperties.DatabaseConfig db) {
        Duration longTx = DurationStyle.detectAndParse(db.getLongTxThreshold());
        DataSourceConfig cfg = new DataSourceConfig(db.getUrl(), db.getUsername(), db.getPassword(),
                db.getConnectTimeoutMs(), db.getQueryTimeoutMs(), longTx);
        return switch (db.getType().toLowerCase()) {
            case "mysql" -> new Resolved(
                    new MysqlDatabaseHealthSource(cfg::openExecutor, cfg, sanitizer),
                    new MysqlSlowSqlSource(cfg::openExecutor));
            case "postgres", "postgresql" -> new Resolved(
                    new PostgresDatabaseHealthSource(cfg::openExecutor, cfg, sanitizer),
                    new PostgresSlowSqlSource(cfg::openExecutor));
            default -> throw new IllegalStateException("Unsupported database.type: " + db.getType());
        };
    }

    private record Resolved(DatabaseHealthSourceAdapter health, SlowSqlSourceAdapter slowSql) {
    }
}
