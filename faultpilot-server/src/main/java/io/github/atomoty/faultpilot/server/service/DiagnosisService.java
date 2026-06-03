package io.github.atomoty.faultpilot.server.service;

import io.github.atomoty.faultpilot.core.budget.ContextBudgeter;
import io.github.atomoty.faultpilot.core.adapter.DiagnosisModel;
import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.DatabaseHealthSnapshot;
import io.github.atomoty.faultpilot.core.model.DiagnosisContext;
import io.github.atomoty.faultpilot.core.model.DiagnosisReport;
import io.github.atomoty.faultpilot.core.model.DiagnosisRequest;
import io.github.atomoty.faultpilot.core.model.Evidence;
import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import io.github.atomoty.faultpilot.core.model.LogCluster;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;
import io.github.atomoty.faultpilot.core.model.ModelOutput;
import io.github.atomoty.faultpilot.core.model.RootCauseCandidate;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;
import io.github.atomoty.faultpilot.core.model.TimelineEntry;
import io.github.atomoty.faultpilot.core.pipeline.CollectedEvidence;
import io.github.atomoty.faultpilot.core.pipeline.EvidenceCollector;
import io.github.atomoty.faultpilot.core.rule.RuleAnalysis;
import io.github.atomoty.faultpilot.core.rule.RuleAnalyzer;
import io.github.atomoty.faultpilot.core.sanitize.EvidenceSanitizer;
import io.github.atomoty.faultpilot.server.config.FaultPilotProperties;
import io.github.atomoty.faultpilot.server.config.ProjectRegistry;
import io.github.atomoty.faultpilot.server.repository.InMemoryDiagnosisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Main diagnosis orchestration. Runs the full pipeline (specification.md §6):
 * validate → parallel collect → sanitize → rule analysis → context budget → model → assemble.
 *
 * <p>Root-cause strength is always taken from the rules; model candidates with no matching rule
 * candidate or no resolvable evidence are dropped (specification.md §10, §11).
 */
@Service
public class DiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProjectRegistry projectRegistry;
    private final EvidenceCollector evidenceCollector;
    private final EvidenceSanitizer sanitizer;
    private final RuleAnalyzer ruleAnalyzer;
    private final ContextBudgeter budgeter;
    private final DiagnosisModel model;
    private final ReportFallbackService fallbackService;
    private final InMemoryDiagnosisRepository repository;
    private final AtomicInteger counter = new AtomicInteger();

    public DiagnosisService(ProjectRegistry projectRegistry,
                            EvidenceCollector evidenceCollector,
                            EvidenceSanitizer sanitizer,
                            RuleAnalyzer ruleAnalyzer,
                            ContextBudgeter budgeter,
                            DiagnosisModel model,
                            ReportFallbackService fallbackService,
                            InMemoryDiagnosisRepository repository) {
        this.projectRegistry = projectRegistry;
        this.evidenceCollector = evidenceCollector;
        this.sanitizer = sanitizer;
        this.ruleAnalyzer = ruleAnalyzer;
        this.budgeter = budgeter;
        this.model = model;
        this.fallbackService = fallbackService;
        this.repository = repository;
    }

    public DiagnosisReport diagnose(DiagnosisRequest request) {
        FaultPilotProperties.Project project =
                projectRegistry.require(request.projectId(), request.environment());
        validateRange(request, project);

        EvidenceQuery query = new EvidenceQuery(request.projectId(), request.environment(),
                request.from(), request.to(), project.getMaxResults());

        // 1. parallel collect (with per-source degradation)
        CollectedEvidence raw = evidenceCollector.collect(query);

        // 2. sanitize all evidence (best-effort): logs, metrics, slow SQL and change-event attributes
        List<LogEvent> logs = raw.logs().stream().map(sanitizer::sanitize).toList();
        List<MetricAnomaly> metrics = raw.metrics().stream().map(sanitizer::sanitize).toList();
        List<SlowSqlSummary> slowSql = raw.slowSql().stream().map(sanitizer::sanitize).toList();
        List<ChangeEvent> changeEvents = raw.changeEvents().stream().map(sanitizer::sanitize).toList();

        // 3. rule analysis (clustering, spikes, timeline, rule candidates)
        RuleAnalysis analysis = ruleAnalyzer.analyze(request.from(), request.to(),
                logs, metrics, slowSql, changeEvents);

        // 4. context budget
        ContextBudgeter.Budgeted budgeted = budgeter.apply(
                analysis.logClusters(), metrics, slowSql, changeEvents);

        // The user question is free text and may carry secrets; redact before it reaches the model.
        DiagnosisRequest sanitizedRequest = new DiagnosisRequest(request.projectId(),
                request.environment(), sanitizer.redact(request.question()),
                request.from(), request.to());

        // Database health snapshots are small (0/1 per source) and not budgeted; carry them as-is.
        List<DatabaseHealthSnapshot> databaseHealth = raw.databaseHealth();

        // Evidence ids that survived budgeting. Timeline entries and rule candidates referencing a
        // truncated id are dropped so every reference resolves (specification.md §11, §8.2).
        List<Evidence> evidence = buildEvidence(budgeted, databaseHealth);
        Set<String> survivingIds = evidence.stream().map(Evidence::evidenceId).collect(Collectors.toSet());

        List<TimelineEntry> timeline = new ArrayList<>(analysis.timeline().stream()
                .filter(t -> t.evidenceId() == null || survivingIds.contains(t.evidenceId()))
                .toList());
        // A DB health snapshot is "current state": place it at the end of the query window.
        for (DatabaseHealthSnapshot s : databaseHealth) {
            if (s.available()) {
                timeline.add(new TimelineEntry(request.to(), "DB_HEALTH",
                        describeDatabaseHealth(s), s.evidenceId()));
            }
        }
        timeline.sort(java.util.Comparator.comparing(TimelineEntry::at,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));

        List<RootCauseCandidate> ruleCandidates = analysis.ruleCandidates().stream()
                .filter(c -> survivingIds.containsAll(c.evidenceIds()))
                .toList();

        DiagnosisContext context = new DiagnosisContext(
                sanitizedRequest,
                budgeted.logClusters(),
                budgeted.metrics(),
                budgeted.slowSql(),
                budgeted.changeEvents(),
                databaseHealth,
                timeline,
                ruleCandidates,
                raw.unavailableSources(),
                budgeted.report());

        String diagnosisId = nextId();

        // 5. model call with rule-only fallback
        DiagnosisReport report;
        try {
            ModelOutput output = model.generate(context);
            report = assemble(diagnosisId, context, output, evidence);
        } catch (RuntimeException ex) {
            log.warn("Model '{}' failed, returning rule fallback report: {}", model.name(), ex.toString());
            // Record the model as an unavailable source so the report is not only flagged by
            // ruleFallback but also names what was unavailable (review P2).
            DiagnosisContext fallbackContext = withUnavailableModel(context, model.name());
            report = fallbackService.build(diagnosisId, fallbackContext, evidence);
        }

        repository.save(report);
        return report;
    }

    private void validateRange(DiagnosisRequest request, FaultPilotProperties.Project project) {
        if (request.from() == null || request.to() == null || !request.from().isBefore(request.to())) {
            throw new InvalidRequestException("时间范围非法: from 必须早于 to");
        }
        Duration span = Duration.between(request.from(), request.to());
        if (span.compareTo(Duration.ofHours(project.getMaxQueryHours())) > 0) {
            throw new InvalidRequestException(
                    "查询跨度超过上限 " + project.getMaxQueryHours() + " 小时");
        }
    }

    /** Keep only model candidates that match a rule candidate (by label) and stamp rule strength. */
    private DiagnosisReport assemble(String diagnosisId, DiagnosisContext context,
                                     ModelOutput output, List<Evidence> evidence) {
        Map<String, RootCauseCandidate> ruleByLabel = new LinkedHashMap<>();
        for (RootCauseCandidate rc : context.ruleCandidates()) {
            ruleByLabel.put(rc.label(), rc);
        }

        List<RootCauseCandidate> accepted = new ArrayList<>();
        for (ModelOutput.Candidate c : output.rootCauseCandidates()) {
            RootCauseCandidate rule = ruleByLabel.get(c.label());
            if (rule == null || rule.evidenceIds().isEmpty()) {
                continue; // no rule backing / no evidence → drop (specification.md §11)
            }
            accepted.add(new RootCauseCandidate(
                    rule.label(), rule.title(), c.explanation(), rule.strength(), rule.evidenceIds()));
        }

        return new DiagnosisReport(
                diagnosisId,
                context.request().projectId(),
                context.request().environment(),
                output.summary(),
                context.timeline(),
                accepted,
                output.recommendedActions(),
                evidence,
                context.unavailableSources(),
                context.budget(),
                false,
                DiagnosisReport.DISCLAIMER,
                Instant.now());
    }

    /** Copy the context, appending the failed model as an unavailable source. */
    private DiagnosisContext withUnavailableModel(DiagnosisContext context, String modelName) {
        List<String> unavailable = new ArrayList<>(context.unavailableSources());
        unavailable.add("model:" + modelName + " (error)");
        return new DiagnosisContext(context.request(), context.logClusters(), context.metricAnomalies(),
                context.slowSqlSummaries(), context.changeEvents(), context.databaseHealth(),
                context.timeline(), context.ruleCandidates(), unavailable, context.budget());
    }

    /** Flatten the budgeted (surviving) evidence into id→description references for the report. */
    private List<Evidence> buildEvidence(ContextBudgeter.Budgeted budgeted,
                                         List<DatabaseHealthSnapshot> databaseHealth) {
        List<Evidence> evidence = new ArrayList<>();
        for (LogCluster c : budgeted.logClusters()) {
            evidence.add(new Evidence(c.evidenceId(), "LOG_CLUSTER",
                    describeLog(c)));
        }
        for (MetricAnomaly m : budgeted.metrics()) {
            evidence.add(new Evidence(m.evidenceId(), "METRIC", m.description()));
        }
        for (SlowSqlSummary s : budgeted.slowSql()) {
            evidence.add(new Evidence(s.evidenceId(), "SLOW_SQL",
                    "avg=" + s.avgDurationMs() + "ms ×" + s.occurrences() + ": " + s.sqlTemplate()));
        }
        for (ChangeEvent e : budgeted.changeEvents()) {
            evidence.add(new Evidence(e.evidenceId(), "CHANGE_EVENT",
                    e.type() + " " + e.attributes()));
        }
        for (DatabaseHealthSnapshot s : databaseHealth) {
            if (s.available()) {
                evidence.add(new Evidence(s.evidenceId(), "DB_HEALTH", describeDatabaseHealth(s)));
            }
        }
        return evidence;
    }

    private String describeDatabaseHealth(DatabaseHealthSnapshot s) {
        return "连接 active=" + s.activeConnections() + " idle=" + s.idleConnections()
                + " waiting=" + s.waitingConnections()
                + "; 长事务 " + s.longTransactions().size()
                + "; 锁等待 " + s.lockWaits().size();
    }

    private String describeLog(LogCluster c) {
        String head = c.exceptionClass() != null ? c.exceptionClass() : c.messageTemplate();
        return (c.spike() ? "突增 " : "") + head + " ×" + c.count();
    }

    private String nextId() {
        return "diag-" + DAY.format(java.time.LocalDate.now()) + "-"
                + String.format("%03d", counter.incrementAndGet());
    }

    /** Thrown for invalid request parameters. Maps to HTTP 400. */
    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }
}
