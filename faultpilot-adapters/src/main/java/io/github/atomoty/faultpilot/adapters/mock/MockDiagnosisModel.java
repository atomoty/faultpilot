package io.github.atomoty.faultpilot.adapters.mock;

import io.github.atomoty.faultpilot.core.adapter.DiagnosisModel;
import io.github.atomoty.faultpilot.core.model.DiagnosisContext;
import io.github.atomoty.faultpilot.core.model.ModelOutput;
import io.github.atomoty.faultpilot.core.model.RootCauseCandidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic stand-in for a real model. It does not invent root causes: it narrates the
 * rule-derived candidates already present in the context and proposes generic, human-confirmable
 * next steps. This keeps the mock report assertable (specification.md §3, §11).
 */
public class MockDiagnosisModel implements DiagnosisModel {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public ModelOutput generate(DiagnosisContext context) {
        List<ModelOutput.Candidate> candidates = new ArrayList<>();
        for (RootCauseCandidate rc : context.ruleCandidates()) {
            candidates.add(new ModelOutput.Candidate(
                    rc.label(), rc.title(), explain(rc.label()), rc.evidenceIds()));
        }
        return new ModelOutput(summary(context), candidates, actions(context));
    }

    private String summary(DiagnosisContext context) {
        if (context.ruleCandidates().isEmpty()) {
            return "未发现明确根因。已聚合 " + context.logClusters().size()
                    + " 类日志与 " + context.timeline().size() + " 条时间线证据,建议人工进一步排查。";
        }
        RootCauseCandidate top = context.ruleCandidates().get(0);
        return "最可能的原因是" + top.title() + "(证据强度 " + top.strength() + ")。详见根因候选与时间线。";
    }

    private String explain(String label) {
        return switch (label) {
            case "deployment-regression" ->
                    "异常突增的时间窗口紧跟在一次发布之后,提示新版本可能引入了缺陷。";
            case "slow-sql-pool-contention" ->
                    "高耗时 SQL 与连接池等待同时出现,慢查询占用连接,导致接口整体变慢。";
            case "slow-sql-latency-correlation" ->
                    "高耗时 SQL 与接口延迟在时间上相关,但当前证据不足以确认因果关系。";
            default -> "依据规则聚合的证据得出的候选解释。";
        };
    }

    private List<String> actions(DiagnosisContext context) {
        List<String> actions = new ArrayList<>();
        for (RootCauseCandidate rc : context.ruleCandidates()) {
            switch (rc.label()) {
                case "deployment-regression" -> {
                    actions.add("核对发布版本对应的提交,定位空值处理改动。");
                    actions.add("人工评估是否回滚到上一个稳定版本。");
                }
                case "slow-sql-pool-contention" -> {
                    actions.add("人工运行 EXPLAIN 评估该 SQL 模板的索引情况。");
                    actions.add("检查连接池容量与慢查询是否需要分离读负载。");
                }
                case "slow-sql-latency-correlation" -> {
                    actions.add("人工运行 EXPLAIN 评估该 SQL 模板的索引情况。");
                    actions.add("补充连接池指标或 traceId,进一步验证慢 SQL 与接口延迟的因果关系。");
                }
                default -> actions.add("结合时间线与证据进行人工复核。");
            }
        }
        if (actions.isEmpty()) {
            actions.add("扩大时间范围或接入更多证据源后重试诊断。");
        }
        return actions;
    }
}
