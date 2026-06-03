package io.github.atomoty.faultpilot.server.service;

import io.github.atomoty.faultpilot.core.model.DiagnosisContext;
import io.github.atomoty.faultpilot.core.model.Evidence;
import io.github.atomoty.faultpilot.core.model.RootCauseCandidate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Builds a rule-only report when the model is unavailable or returns invalid output
 * (specification.md §3, design.md §20.3). Uses only deterministic rule output.
 */
@Service
public class ReportFallbackService {

    public io.github.atomoty.faultpilot.core.model.DiagnosisReport build(
            String diagnosisId, DiagnosisContext context, List<Evidence> evidence) {

        String summary = context.ruleCandidates().isEmpty()
                ? "模型不可用,且规则未识别出明确根因。已返回聚合证据与时间线供人工排查。"
                : "模型不可用,以下为规则分析得出的候选根因(降级报告)。";

        List<RootCauseCandidate> candidates = context.ruleCandidates();

        return new io.github.atomoty.faultpilot.core.model.DiagnosisReport(
                diagnosisId,
                context.request().projectId(),
                context.request().environment(),
                summary,
                context.timeline(),
                candidates,
                List.of("结合时间线与证据进行人工复核。"),
                evidence,
                context.unavailableSources(),
                context.budget(),
                true,
                io.github.atomoty.faultpilot.core.model.DiagnosisReport.DISCLAIMER,
                Instant.now());
    }
}
