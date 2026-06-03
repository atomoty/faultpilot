package io.github.atomoty.faultpilot.core.rule;

import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.EvidenceStrength;
import io.github.atomoty.faultpilot.core.model.LogCluster;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;
import io.github.atomoty.faultpilot.core.model.RootCauseCandidate;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;
import io.github.atomoty.faultpilot.core.sanitize.EvidenceSanitizer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleAnalyzerTest {

    private static final Instant TO = Instant.parse("2026-06-01T03:00:00Z");
    private static final Instant FROM = TO.minusSeconds(2 * 3600);

    private final RuleAnalyzer analyzer =
            new RuleAnalyzer(new EvidenceSanitizer(), SpikeThresholds.defaults());

    private LogEvent npe(Instant at) {
        return new LogEvent("order-service", "staging", at, "ERROR",
                "com.example.order.OrderService", "t", "Create order failed",
                "java.lang.NullPointerException: x\n\tat com.example.order.OrderService.create(OrderService.java:88)",
                Map.of("exceptionClass", "NullPointerException"));
    }

    @Test
    void clustersByNormalizedKeyAndDetectsSpike() {
        List<LogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            logs.add(npe(TO.minusSeconds(60L * (i + 1))));   // current 10-min window
        }
        logs.add(npe(TO.minusSeconds(40 * 60)));             // baseline

        RuleAnalysis analysis = analyzer.analyze(FROM, TO, logs, List.of(), List.of(), List.of());

        assertThat(analysis.logClusters()).hasSize(1);
        LogCluster cluster = analysis.logClusters().get(0);
        assertThat(cluster.count()).isEqualTo(9);
        assertThat(cluster.spike()).isTrue();      // 8 current >= 5 and >= baseline(1) * 3
    }

    @Test
    void noSpikeBelowAbsoluteThreshold() {
        List<LogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            logs.add(npe(TO.minusSeconds(60L * (i + 1))));   // only 4 < absolute threshold 5
        }
        RuleAnalysis analysis = analyzer.analyze(FROM, TO, logs, List.of(), List.of(), List.of());
        assertThat(analysis.logClusters().get(0).spike()).isFalse();
    }

    @Test
    void deploymentBeforeSpikeProducesStrongCandidate() {
        List<LogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            logs.add(npe(TO.minusSeconds(60L * (i + 1))));
        }
        ChangeEvent deploy = new ChangeEvent("deploy-1", "order-service", "staging",
                "DEPLOYMENT", TO.minusSeconds(20 * 60), Map.of());

        RuleAnalysis analysis = analyzer.analyze(FROM, TO, logs, List.of(), List.of(), List.of(deploy));

        RootCauseCandidate candidate = analysis.ruleCandidates().stream()
                .filter(c -> c.label().equals("deployment-regression")).findFirst().orElseThrow();
        assertThat(candidate.strength()).isEqualTo(EvidenceStrength.STRONG);
        assertThat(candidate.evidenceIds()).contains("deploy-1");
        assertThat(candidate.evidenceIds()).anyMatch(id -> id.startsWith("log-"));
    }

    @Test
    void laterRelatedDeploymentIsNotHiddenByEarlierUnrelatedDeployment() {
        List<LogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            logs.add(npe(TO.minusSeconds(60L * (i + 1))));
        }
        ChangeEvent unrelated = new ChangeEvent("deploy-old", "order-service", "staging",
                "DEPLOYMENT", TO.minusSeconds(3 * 3600), Map.of());
        ChangeEvent related = new ChangeEvent("deploy-related", "order-service", "staging",
                "DEPLOYMENT", TO.minusSeconds(20 * 60), Map.of());

        RuleAnalysis analysis = analyzer.analyze(
                FROM, TO, logs, List.of(), List.of(), List.of(unrelated, related));

        RootCauseCandidate candidate = analysis.ruleCandidates().stream()
                .filter(c -> c.label().equals("deployment-regression")).findFirst().orElseThrow();
        assertThat(candidate.evidenceIds()).contains("deploy-related");
    }

    @Test
    void spikeShortlyBeforeDeploymentIsWithinClockSkewTolerance() {
        List<LogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            logs.add(npe(TO.minusSeconds(60L * (i + 1))));
        }
        ChangeEvent deploy = new ChangeEvent("deploy-1", "order-service", "staging",
                "DEPLOYMENT", TO.plusSeconds(5 * 60), Map.of());

        RuleAnalysis analysis = analyzer.analyze(FROM, TO, logs, List.of(), List.of(), List.of(deploy));

        assertThat(analysis.ruleCandidates())
                .anyMatch(c -> c.label().equals("deployment-regression"));
    }

    @Test
    void slowSqlWithPoolMetricProducesContentionCandidate() {
        SlowSqlSummary sql = new SlowSqlSummary("sql-1", "select * from orders where id = ?",
                300, 2100, 2800, TO.minusSeconds(30 * 60), "mock");
        MetricAnomaly pool = new MetricAnomaly("metric-1", "db_pool_pending_connections",
                "pool waits", 0, 12, "count", TO.minusSeconds(28 * 60));

        RuleAnalysis analysis = analyzer.analyze(FROM, TO, List.of(), List.of(pool), List.of(sql), List.of());

        RootCauseCandidate candidate = analysis.ruleCandidates().stream()
                .filter(c -> c.label().equals("slow-sql-pool-contention")).findFirst().orElseThrow();
        assertThat(candidate.evidenceIds()).containsExactlyInAnyOrder("sql-1", "metric-1");
        assertThat(candidate.evidenceIds()).isNotEmpty();
    }

    @Test
    void slowSqlWithUnrelatedMetricProducesNoSqlCandidate() {
        SlowSqlSummary sql = new SlowSqlSummary("sql-1", "select * from orders where id = ?",
                300, 2100, 2800, TO.minusSeconds(30 * 60), "mock");
        // Neither pool nor latency → must not be treated as causal/correlated evidence at all.
        MetricAnomaly unrelated = new MetricAnomaly("metric-1", "cpu_usage_percent",
                "cpu", 20, 40, "percent", TO.minusSeconds(28 * 60));

        RuleAnalysis analysis = analyzer.analyze(FROM, TO, List.of(), List.of(unrelated), List.of(sql), List.of());

        assertThat(analysis.ruleCandidates())
                .noneMatch(c -> c.label().startsWith("slow-sql-"));
    }

    @Test
    void slowSqlWithLatencyMetricProducesWeakerCorrelationCandidate() {
        SlowSqlSummary sql = new SlowSqlSummary("sql-1", "select * from orders where id = ?",
                300, 2100, 2800, TO.minusSeconds(30 * 60), "mock");
        // Latency/P95 only (no pool metric) → correlation candidate, MODERATE, not a causal claim.
        MetricAnomaly latency = new MetricAnomaly("metric-1", "http_p95_seconds",
                "p95 up", 0.3, 2.8, "s", TO.minusSeconds(28 * 60));

        RuleAnalysis analysis = analyzer.analyze(FROM, TO, List.of(), List.of(latency), List.of(sql), List.of());

        assertThat(analysis.ruleCandidates())
                .noneMatch(c -> c.label().equals("slow-sql-pool-contention"));
        RootCauseCandidate candidate = analysis.ruleCandidates().stream()
                .filter(c -> c.label().equals("slow-sql-latency-correlation")).findFirst().orElseThrow();
        assertThat(candidate.strength()).isEqualTo(EvidenceStrength.MODERATE);
        assertThat(candidate.evidenceIds()).containsExactlyInAnyOrder("sql-1", "metric-1");
    }

    @Test
    void poolMetricPreferredOverLatencyWhenBothPresent() {
        SlowSqlSummary sql = new SlowSqlSummary("sql-1", "select * from orders where id = ?",
                300, 2100, 2800, TO.minusSeconds(30 * 60), "mock");
        MetricAnomaly latency = new MetricAnomaly("metric-1", "http_p95_seconds",
                "p95 up", 0.3, 2.8, "s", TO.minusSeconds(29 * 60));
        MetricAnomaly pool = new MetricAnomaly("metric-2", "db_pool_pending_connections",
                "pool waits", 0, 12, "count", TO.minusSeconds(28 * 60));

        RuleAnalysis analysis = analyzer.analyze(FROM, TO, List.of(), List.of(latency, pool), List.of(sql), List.of());

        RootCauseCandidate candidate = analysis.ruleCandidates().stream()
                .filter(c -> c.label().startsWith("slow-sql-")).findFirst().orElseThrow();
        assertThat(candidate.label()).isEqualTo("slow-sql-pool-contention");
        assertThat(candidate.strength()).isEqualTo(EvidenceStrength.STRONG);
        assertThat(candidate.evidenceIds()).contains("metric-2");
    }

    @Test
    void slowSqlWithPoolMetricOutsideWindowProducesNoCandidate() {
        SlowSqlSummary sql = new SlowSqlSummary("sql-1", "select * from orders where id = ?",
                300, 2100, 2800, TO.minusSeconds(110 * 60), "mock");
        // pool metric is correct type but >10min away from the slow SQL → not correlated.
        MetricAnomaly pool = new MetricAnomaly("metric-1", "db_pool_pending_connections",
                "pool waits", 0, 12, "count", TO.minusSeconds(20 * 60));

        RuleAnalysis analysis = analyzer.analyze(FROM, TO, List.of(), List.of(pool), List.of(sql), List.of());

        assertThat(analysis.ruleCandidates())
                .noneMatch(c -> c.label().equals("slow-sql-pool-contention"));
    }

    @Test
    void correlatedSqlIsNotHiddenBySlowerSqlOutsideWindow() {
        SlowSqlSummary unrelatedWorst = new SlowSqlSummary("sql-worst", "select * from audit where id = ?",
                10, 5000, 6000, TO.minusSeconds(110 * 60), "mock");
        SlowSqlSummary correlated = new SlowSqlSummary("sql-related", "select * from orders where id = ?",
                300, 2100, 2800, TO.minusSeconds(30 * 60), "mock");
        MetricAnomaly pool = new MetricAnomaly("metric-1", "db_pool_pending_connections",
                "pool waits", 0, 12, "count", TO.minusSeconds(28 * 60));

        RuleAnalysis analysis = analyzer.analyze(
                FROM, TO, List.of(), List.of(pool), List.of(unrelatedWorst, correlated), List.of());

        RootCauseCandidate candidate = analysis.ruleCandidates().stream()
                .filter(c -> c.label().equals("slow-sql-pool-contention")).findFirst().orElseThrow();
        assertThat(candidate.evidenceIds()).containsExactlyInAnyOrder("sql-related", "metric-1");
    }
}
