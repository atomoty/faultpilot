package io.github.atomoty.faultpilot.core.rule;

import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.EvidenceStrength;
import io.github.atomoty.faultpilot.core.model.LogCluster;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;
import io.github.atomoty.faultpilot.core.model.RootCauseCandidate;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;
import io.github.atomoty.faultpilot.core.model.TimelineEntry;
import io.github.atomoty.faultpilot.core.sanitize.EvidenceSanitizer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic analysis: clustering, spike detection, timeline assembly and rule-derived
 * root-cause candidates. The model never does any of this. See specification.md §3.1, §7.
 *
 * <p>Inputs are expected to be already sanitized. Clustering keys are built with
 * {@link EvidenceSanitizer} normalization helpers.
 */
public class RuleAnalyzer {

    /** Width of the "current" window at the end of the range used for spike comparison (§7.5). */
    private static final Duration CURRENT_WINDOW = Duration.ofMinutes(10);

    /** Deployment-to-anomaly correlation window: deploy-10m .. deploy+60m (§7.3). */
    private static final Duration DEPLOY_BACKWARD = Duration.ofMinutes(10);
    private static final Duration DEPLOY_FORWARD = Duration.ofMinutes(60);

    private final EvidenceSanitizer sanitizer;
    private final SpikeThresholds thresholds;

    public RuleAnalyzer(EvidenceSanitizer sanitizer, SpikeThresholds thresholds) {
        this.sanitizer = sanitizer;
        this.thresholds = thresholds;
    }

    public RuleAnalysis analyze(
            Instant from,
            Instant to,
            List<LogEvent> logs,
            List<MetricAnomaly> metrics,
            List<SlowSqlSummary> slowSql,
            List<ChangeEvent> changeEvents) {

        List<LogCluster> clusters = cluster(logs, from, to);
        List<TimelineEntry> timeline = buildTimeline(clusters, metrics, slowSql, changeEvents);
        List<RootCauseCandidate> candidates =
                deriveCandidates(clusters, metrics, slowSql, changeEvents);

        return new RuleAnalysis(clusters, timeline, candidates);
    }

    // --- clustering -------------------------------------------------------------------------

    private List<LogCluster> cluster(List<LogEvent> logs, Instant from, Instant to) {
        Instant currentWindowStart = to.minus(CURRENT_WINDOW);

        Map<String, Accumulator> byKey = new LinkedHashMap<>();
        for (LogEvent event : logs) {
            String exceptionClass = normalizeExceptionClass(event);
            String messageTemplate = sanitizer.normalizeMessage(event.message());
            String topFrame = sanitizer.topStackFrame(event.stackTrace());
            String key = String.join("|",
                    event.projectId(),
                    event.environment(),
                    String.valueOf(exceptionClass),
                    String.valueOf(messageTemplate),
                    String.valueOf(topFrame));

            Accumulator acc = byKey.computeIfAbsent(key,
                    k -> new Accumulator(key, exceptionClass, messageTemplate, topFrame, event.level()));
            acc.add(event, currentWindowStart);
        }

        // Whether a usable baseline exists: the range must be wider than the current window.
        boolean hasBaseline = Duration.between(from, to).compareTo(CURRENT_WINDOW) > 0;

        List<LogCluster> clusters = new ArrayList<>();
        int idx = 1;
        for (Accumulator acc : byKey.values()) {
            String evidenceId = "log-" + idx++;
            boolean spike = thresholds.isSpike(acc.currentCount, acc.baselineCount, hasBaseline);
            clusters.add(acc.toCluster(evidenceId, spike));
        }
        clusters.sort(Comparator.comparingLong(LogCluster::count).reversed());
        return clusters;
    }

    private String normalizeExceptionClass(LogEvent event) {
        String explicit = event.attributes().get("exceptionClass");
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        // Fall back to the first token of the stack trace (e.g. "java.lang.NullPointerException: ...").
        if (event.stackTrace() != null && !event.stackTrace().isBlank()) {
            String firstLine = event.stackTrace().split("\\R", 2)[0];
            int colon = firstLine.indexOf(':');
            return colon > 0 ? firstLine.substring(0, colon).trim() : firstLine.trim();
        }
        return null;
    }

    private static final class Accumulator {
        final String key;
        final String exceptionClass;
        final String messageTemplate;
        final String topFrame;
        final String level;
        long currentCount;
        long baselineCount;
        Instant firstSeen;
        Instant lastSeen;
        LogEvent sample;

        Accumulator(String key, String exceptionClass, String messageTemplate, String topFrame, String level) {
            this.key = key;
            this.exceptionClass = exceptionClass;
            this.messageTemplate = messageTemplate;
            this.topFrame = topFrame;
            this.level = level;
        }

        void add(LogEvent event, Instant currentWindowStart) {
            Instant at = event.occurredAt();
            if (!at.isBefore(currentWindowStart)) {
                currentCount++;
            } else {
                baselineCount++;
            }
            if (firstSeen == null || at.isBefore(firstSeen)) {
                firstSeen = at;
            }
            if (lastSeen == null || at.isAfter(lastSeen)) {
                lastSeen = at;
            }
            if (sample == null) {
                sample = event;
            }
        }

        LogCluster toCluster(String evidenceId, boolean spike) {
            return new LogCluster(evidenceId, key, exceptionClass, messageTemplate, topFrame, level,
                    currentCount + baselineCount, baselineCount, spike, firstSeen, lastSeen, sample);
        }
    }

    // --- timeline ---------------------------------------------------------------------------

    private List<TimelineEntry> buildTimeline(
            List<LogCluster> clusters,
            List<MetricAnomaly> metrics,
            List<SlowSqlSummary> slowSql,
            List<ChangeEvent> changeEvents) {

        List<TimelineEntry> timeline = new ArrayList<>();
        for (ChangeEvent e : changeEvents) {
            timeline.add(new TimelineEntry(e.occurredAt(), "CHANGE", e.type(), e.evidenceId()));
        }
        for (LogCluster c : clusters) {
            String desc = (c.spike() ? "异常突增: " : "异常: ") + describeCluster(c) + " ×" + c.count();
            timeline.add(new TimelineEntry(c.firstSeen(), "LOG", desc, c.evidenceId()));
        }
        for (MetricAnomaly m : metrics) {
            timeline.add(new TimelineEntry(m.observedAt(), "METRIC", m.description(), m.evidenceId()));
        }
        for (SlowSqlSummary s : slowSql) {
            String desc = "慢 SQL avg=" + s.avgDurationMs() + "ms: " + s.sqlTemplate();
            timeline.add(new TimelineEntry(s.firstSeen(), "SLOW_SQL", desc, s.evidenceId()));
        }
        timeline.sort(Comparator.comparing(TimelineEntry::at, Comparator.nullsLast(Comparator.naturalOrder())));
        return timeline;
    }

    private String describeCluster(LogCluster c) {
        if (c.exceptionClass() != null) {
            return c.exceptionClass();
        }
        return c.messageTemplate() != null ? c.messageTemplate() : c.level();
    }

    // --- rule candidates --------------------------------------------------------------------

    private List<RootCauseCandidate> deriveCandidates(
            List<LogCluster> clusters,
            List<MetricAnomaly> metrics,
            List<SlowSqlSummary> slowSql,
            List<ChangeEvent> changeEvents) {

        List<RootCauseCandidate> candidates = new ArrayList<>();
        deploymentRegression(clusters, changeEvents).ifPresent(candidates::add);
        slowSqlMetricCandidate(slowSql, metrics).ifPresent(candidates::add);
        return candidates;
    }

    /** A deployment shortly before a spike cluster => deployment-regression. */
    private java.util.Optional<RootCauseCandidate> deploymentRegression(
            List<LogCluster> clusters, List<ChangeEvent> changeEvents) {

        for (ChangeEvent deploy : changeEvents) {
            if (!"DEPLOYMENT".equals(deploy.type()) && !"ROLLBACK".equals(deploy.type())) {
                continue;
            }
            // The spike's elevated activity is in the recent window, so test lastSeen against the
            // deploy window: a baseline occurrence may pull firstSeen before the deploy.
            LogCluster spike = clusters.stream()
                    .filter(LogCluster::spike)
                    .filter(c -> withinDeployWindow(deploy.occurredAt(), c.lastSeen()))
                    .findFirst().orElse(null);
            if (spike != null) {
                // Two independent sources (change event + log spike) + explicit spike rule hit => STRONG.
                EvidenceStrength strength = EvidenceStrengthRule.compute(2, false, true);
                return java.util.Optional.of(new RootCauseCandidate(
                        "deployment-regression",
                        "发布后引入异常",
                        null,
                        strength,
                        List.of(deploy.evidenceId(), spike.evidenceId())));
            }
        }
        return java.util.Optional.empty();
    }

    private boolean withinDeployWindow(Instant deployAt, Instant anomalyAt) {
        if (deployAt == null || anomalyAt == null) {
            return false;
        }
        return !anomalyAt.isBefore(deployAt.minus(DEPLOY_BACKWARD))
                && !anomalyAt.isAfter(deployAt.plus(DEPLOY_FORWARD));
    }

    /** Correlation window between a slow-SQL summary and a pool/latency metric anomaly. */
    private static final Duration SQL_METRIC_WINDOW = Duration.ofMinutes(10);

    /**
     * Slow SQL correlated (within the window) with either:
     * <ul>
     *   <li>a connection-pool metric → {@code slow-sql-pool-contention} (STRONG, causal): the pool is
     *       the mechanism by which a slow query degrades the endpoint;</li>
     *   <li>otherwise a latency/P95 metric → {@code slow-sql-latency-correlation} (MODERATE,
     *       correlation only): co-movement is observed but the causal mechanism is not established.</li>
     * </ul>
     * A pool metric is preferred when both are present. Without any qualifying, time-correlated
     * metric no candidate is emitted — an unrelated metric must never produce a causal claim.
     */
    private java.util.Optional<RootCauseCandidate> slowSqlMetricCandidate(
            List<SlowSqlSummary> slowSql, List<MetricAnomaly> metrics) {

        if (slowSql.isEmpty() || metrics.isEmpty()) {
            return java.util.Optional.empty();
        }
        List<SlowSqlSummary> byDuration = slowSql.stream()
                .sorted(Comparator.comparingLong(SlowSqlSummary::avgDurationMs).reversed())
                .toList();

        for (SlowSqlSummary sql : byDuration) {
            MetricAnomaly poolMetric = correlatedMetric(sql, metrics, this::isPoolMetric);
            if (poolMetric != null) {
                // Two independent sources + explicit pool mechanism => STRONG causal.
                return java.util.Optional.of(new RootCauseCandidate(
                        "slow-sql-pool-contention",
                        "慢 SQL 导致连接池等待",
                        null,
                        EvidenceStrengthRule.compute(2, false, true),
                        List.of(sql.evidenceId(), poolMetric.evidenceId())));
            }
        }

        for (SlowSqlSummary sql : byDuration) {
            MetricAnomaly latencyMetric = correlatedMetric(sql, metrics, this::isLatencyMetric);
            if (latencyMetric != null) {
                // Co-movement only, no established mechanism => MODERATE, correlation wording.
                return java.util.Optional.of(new RootCauseCandidate(
                        "slow-sql-latency-correlation",
                        "慢 SQL 与接口延迟时间相关",
                        null,
                        EvidenceStrengthRule.compute(2, false, false),
                        List.of(sql.evidenceId(), latencyMetric.evidenceId())));
            }
        }

        return java.util.Optional.empty();
    }

    private MetricAnomaly correlatedMetric(SlowSqlSummary sql, List<MetricAnomaly> metrics,
                                           java.util.function.Predicate<String> kind) {
        return metrics.stream()
                .filter(m -> kind.test(m.metric()))
                .filter(m -> withinWindow(sql.firstSeen(), m.observedAt(), SQL_METRIC_WINDOW))
                .findFirst().orElse(null);
    }

    private boolean withinWindow(Instant a, Instant b, Duration window) {
        if (a == null || b == null) {
            return false;
        }
        return Duration.between(a, b).abs().compareTo(window) <= 0;
    }

    private boolean isPoolMetric(String metric) {
        if (metric == null) {
            return false;
        }
        String m = metric.toLowerCase();
        return m.contains("pool") || m.contains("connection");
    }

    private boolean isLatencyMetric(String metric) {
        if (metric == null) {
            return false;
        }
        String m = metric.toLowerCase();
        return m.contains("p95") || m.contains("p99") || m.contains("latency") || m.contains("duration");
    }
}
