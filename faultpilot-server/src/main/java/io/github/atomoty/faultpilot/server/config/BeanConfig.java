package io.github.atomoty.faultpilot.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.adapters.codex.CodexCliDiagnosisModel;
import io.github.atomoty.faultpilot.adapters.codex.CodexConfig;
import io.github.atomoty.faultpilot.adapters.codex.JdkProcessRunner;
import io.github.atomoty.faultpilot.adapters.mock.MockChangeEventSource;
import io.github.atomoty.faultpilot.adapters.mock.MockDiagnosisModel;
import io.github.atomoty.faultpilot.adapters.mock.MockLogSourceAdapter;
import io.github.atomoty.faultpilot.adapters.mock.MockMetricSourceAdapter;
import io.github.atomoty.faultpilot.adapters.mock.MockSlowSqlSourceAdapter;
import io.github.atomoty.faultpilot.adapters.openai.JdkHttpInvoker;
import io.github.atomoty.faultpilot.adapters.openai.OpenAiApiDiagnosisModel;
import io.github.atomoty.faultpilot.adapters.openai.OpenAiConfig;
import io.github.atomoty.faultpilot.core.adapter.ChangeEventSource;
import io.github.atomoty.faultpilot.core.adapter.DatabaseHealthSourceAdapter;
import io.github.atomoty.faultpilot.core.adapter.DiagnosisModel;
import io.github.atomoty.faultpilot.core.adapter.LogSourceAdapter;
import io.github.atomoty.faultpilot.core.adapter.MetricSourceAdapter;
import io.github.atomoty.faultpilot.core.adapter.SlowSqlSourceAdapter;
import io.github.atomoty.faultpilot.core.budget.ContextBudget;
import io.github.atomoty.faultpilot.core.budget.ContextBudgeter;
import io.github.atomoty.faultpilot.core.jdbc.JdbcLogReader;
import io.github.atomoty.faultpilot.core.log.LocalFileLogReader;
import io.github.atomoty.faultpilot.core.log.LogLineParser;
import io.github.atomoty.faultpilot.core.pipeline.EvidenceCollector;
import io.github.atomoty.faultpilot.core.rule.RuleAnalyzer;
import io.github.atomoty.faultpilot.core.rule.SpikeThresholds;
import io.github.atomoty.faultpilot.core.sanitize.EvidenceSanitizer;
import io.github.atomoty.faultpilot.server.adapter.DatabaseSourceRouter;
import io.github.atomoty.faultpilot.server.adapter.RoutingLogSourceAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Wires core pipeline components and v0.1.0 adapters. The diagnosis model is selected by
 * {@code faultpilot.ai.provider}: {@code mock}, {@code openai-api}, or experimental {@code codex-cli}.
 */
@Configuration
public class BeanConfig {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BeanConfig.class);

    @Bean
    EvidenceSanitizer evidenceSanitizer() {
        return new EvidenceSanitizer();
    }

    @Bean
    RuleAnalyzer ruleAnalyzer(EvidenceSanitizer sanitizer) {
        return new RuleAnalyzer(sanitizer, SpikeThresholds.defaults());
    }

    @Bean
    ContextBudgeter contextBudgeter() {
        return new ContextBudgeter(ContextBudget.defaults());
    }

    // --- log source: routing adapter delegates to mock / local-file / jdbc per project config ---

    /** Declared as the concrete type so it is NOT collected directly as a {@link LogSourceAdapter}. */
    @Bean
    MockLogSourceAdapter mockLogSourceAdapter() {
        return new MockLogSourceAdapter();
    }

    @Bean
    LocalFileLogReader localFileLogReader() {
        return new LocalFileLogReader(new LogLineParser());
    }

    @Bean
    JdbcLogReader jdbcLogReader() {
        return new JdbcLogReader();
    }

    /** The primary {@link LogSourceAdapter} collected by {@link EvidenceCollector}. */
    @Bean
    LogSourceAdapter routingLogSourceAdapter(ProjectRegistry projectRegistry,
                                             MockLogSourceAdapter mockAdapter,
                                             LocalFileLogReader localFileReader,
                                             JdbcLogReader jdbcLogReader) {
        return new RoutingLogSourceAdapter(projectRegistry, mockAdapter, localFileReader, jdbcLogReader);
    }

    // --- other mock adapters (v0.1.0) ---

    @Bean
    MetricSourceAdapter mockMetricSourceAdapter() {
        return new MockMetricSourceAdapter();
    }

    /** Concrete-type bean (not collected directly); the routing slow-SQL adapter delegates to it. */
    @Bean
    MockSlowSqlSourceAdapter mockSlowSqlSourceAdapter() {
        return new MockSlowSqlSourceAdapter();
    }

    // --- database analysis (mysql/postgres), per-project routed ---

    @Bean
    DatabaseSourceRouter databaseSourceRouter(ProjectRegistry projectRegistry, EvidenceSanitizer sanitizer) {
        return new DatabaseSourceRouter(projectRegistry, sanitizer);
    }

    /** Primary {@link DatabaseHealthSourceAdapter}: routes per project's {@code database.type}. */
    @Bean
    DatabaseHealthSourceAdapter databaseHealthSourceAdapter(DatabaseSourceRouter router) {
        return router.healthAdapter();
    }

    /**
     * Primary {@link SlowSqlSourceAdapter}: a project with a database block reads slow SQL from the DB;
     * otherwise it falls back to the mock fixtures. This avoids double-collecting from both sources.
     */
    @Bean
    SlowSqlSourceAdapter routingSlowSqlSourceAdapter(ProjectRegistry projectRegistry,
                                                     MockSlowSqlSourceAdapter mockSlowSql,
                                                     DatabaseSourceRouter router) {
        SlowSqlSourceAdapter dbSlowSql = router.slowSqlAdapter();
        return query -> projectRegistry.find(query.projectId())
                .map(p -> p.getDatabase().isEnabled() ? dbSlowSql.query(query) : mockSlowSql.query(query))
                .orElseGet(() -> mockSlowSql.query(query));
    }

    @Bean
    ChangeEventSource mockChangeEventSource() {
        return new MockChangeEventSource();
    }

    /**
     * Selects the diagnosis model by {@code faultpilot.ai.provider}: {@code mock} (default),
     * {@code openai-api} (API key), or experimental local {@code codex-cli}. Missing required config
     * (or an unknown provider) fails fast at startup rather than being silently ignored (review #9).
     */
    @Bean
    DiagnosisModel diagnosisModel(FaultPilotProperties properties, ObjectMapper objectMapper) {
        FaultPilotProperties.Ai ai = properties.getAi();
        String provider = ai.getProvider() == null ? "mock" : ai.getProvider();
        return switch (provider.toLowerCase()) {
            case "mock" -> new MockDiagnosisModel();
            case "openai-api" -> new OpenAiApiDiagnosisModel(
                    new OpenAiConfig(ai.getBaseUrl(), ai.getApiKey(), ai.getModel(), ai.getTimeout()),
                    new JdkHttpInvoker(ai.getTimeout()), objectMapper);
            case "codex-cli" -> {
                log.warn("AI provider 'codex-cli' is experimental and local-only; never use it for "
                        + "online deployment. It reuses your existing 'codex login' and never reads credentials.");
                yield new CodexCliDiagnosisModel(
                        new CodexConfig(ai.getCodexCommand(), ai.getCodexModel(), ai.getCodexTimeout()),
                        new JdkProcessRunner(), objectMapper);
            }
            default -> throw new IllegalStateException("不支持的 AI provider: '" + provider
                    + "'。支持 'mock' / 'openai-api' / 'codex-cli'。");
        };
    }

    // --- collector ---

    /**
     * Bounded pool for evidence collection. A bounded queue with an abort policy means that once
     * workers are saturated (e.g. a real source hangs), further submissions are rejected and the
     * collector degrades that source rather than queuing unboundedly (review #6 mitigation).
     * Real cancellation of a stuck JDBC call needs JDBC-level timeouts, added with that adapter.
     */
    @Bean(destroyMethod = "shutdown")
    ExecutorService evidenceExecutor() {
        return new ThreadPoolExecutor(
                4, 8, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Collects from all log/metric/slow-sql source adapters plus every {@link ChangeEventSource}
     * bean. The latter list includes both the mock fixtures and the {@code InMemoryEventStore}
     * written via POST /events, since it is itself a {@link ChangeEventSource}.
     */
    @Bean
    EvidenceCollector evidenceCollector(List<LogSourceAdapter> logSources,
                                        List<MetricSourceAdapter> metricSources,
                                        List<SlowSqlSourceAdapter> slowSqlSources,
                                        List<ChangeEventSource> changeEventSources,
                                        List<DatabaseHealthSourceAdapter> databaseHealthSources,
                                        ExecutorService evidenceExecutor) {
        return new EvidenceCollector(logSources, metricSources, slowSqlSources,
                changeEventSources, databaseHealthSources, Duration.ofSeconds(5), evidenceExecutor);
    }
}
