package io.github.atomoty.faultpilot.server.adapter;

import io.github.atomoty.faultpilot.adapters.mock.MockLogSourceAdapter;
import io.github.atomoty.faultpilot.core.adapter.LogSourceAdapter;
import io.github.atomoty.faultpilot.core.jdbc.JdbcLogReader;
import io.github.atomoty.faultpilot.core.jdbc.JdbcLogSource;
import io.github.atomoty.faultpilot.core.log.LocalFileLogReader;
import io.github.atomoty.faultpilot.core.log.LocalFileLogSource;
import io.github.atomoty.faultpilot.core.log.LogLineParser;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import io.github.atomoty.faultpilot.server.config.FaultPilotProperties;
import io.github.atomoty.faultpilot.server.config.ProjectRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The primary {@link LogSourceAdapter}. Routes a query to the per-project log source declared by
 * {@code faultpilot.projects[].logs.type}: {@code mock} → built-in fixtures, {@code local-file} →
 * {@link LocalFileLogReader}. Unknown types degrade to no logs (the source is simply absent).
 *
 * <p>For {@code local-file}, the resolved {@link LocalFileLogSource} and its (possibly custom-pattern)
 * parser are built once per project and cached, so charset/zone/pattern are validated eagerly and not
 * rebuilt or recompiled on every query.
 */
public class RoutingLogSourceAdapter implements LogSourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(RoutingLogSourceAdapter.class);

    private final ProjectRegistry projectRegistry;
    private final MockLogSourceAdapter mockAdapter;
    private final LocalFileLogReader localFileReader;
    private final JdbcLogReader jdbcLogReader;
    private final LogLineParser defaultParser = new LogLineParser();
    private final Map<String, ResolvedLocalFile> localFileCache = new ConcurrentHashMap<>();
    private final Map<String, JdbcLogSource> jdbcCache = new ConcurrentHashMap<>();

    public RoutingLogSourceAdapter(ProjectRegistry projectRegistry,
                                   MockLogSourceAdapter mockAdapter,
                                   LocalFileLogReader localFileReader,
                                   JdbcLogReader jdbcLogReader) {
        this.projectRegistry = projectRegistry;
        this.mockAdapter = mockAdapter;
        this.localFileReader = localFileReader;
        this.jdbcLogReader = jdbcLogReader;
    }

    @Override
    public List<LogEvent> query(EvidenceQuery query) {
        FaultPilotProperties.LogsConfig logs = projectRegistry.find(query.projectId())
                .map(FaultPilotProperties.Project::getLogs)
                .orElse(null);
        if (logs == null) {
            return List.of();
        }
        String type = logs.getType() == null ? "mock" : logs.getType();
        return switch (type) {
            case "mock" -> mockAdapter.query(query);
            case "local-file" -> {
                ResolvedLocalFile resolved =
                        localFileCache.computeIfAbsent(query.projectId(), id -> resolve(logs));
                yield localFileReader.read(resolved.source(), resolved.parser(), query);
            }
            case "jdbc" -> {
                JdbcLogSource source =
                        jdbcCache.computeIfAbsent(query.projectId(), id -> resolveJdbc(logs));
                yield jdbcLogReader.read(source, query);
            }
            default -> {
                log.warn("Unknown logs.type '{}' for project {}, returning no logs",
                        type, query.projectId());
                yield List.of();
            }
        };
    }

    /** Built once per project; throws here (at first use) if charset/zone/pattern are invalid. */
    private ResolvedLocalFile resolve(FaultPilotProperties.LogsConfig logs) {
        LocalFileLogSource source = new LocalFileLogSource(
                logs.getPaths(), logs.getPattern(), Charset.forName(logs.getCharset()), zoneOf(logs));
        LogLineParser parser = (logs.getPattern() == null || logs.getPattern().isBlank())
                ? defaultParser : new LogLineParser(logs.getPattern());
        return new ResolvedLocalFile(source, parser);
    }

    /** Built once per project; the view identifier is validated lazily by the reader on each query. */
    private JdbcLogSource resolveJdbc(FaultPilotProperties.LogsConfig logs) {
        return new JdbcLogSource(logs.getUrl(), logs.getUsername(), logs.getPassword(),
                logs.getView(), logs.getConnectTimeoutMs(), logs.getQueryTimeoutMs(), zoneOf(logs));
    }

    private ZoneId zoneOf(FaultPilotProperties.LogsConfig logs) {
        return (logs.getZone() == null || logs.getZone().isBlank())
                ? ZoneId.systemDefault() : ZoneId.of(logs.getZone());
    }

    private record ResolvedLocalFile(LocalFileLogSource source, LogLineParser parser) {
    }
}
