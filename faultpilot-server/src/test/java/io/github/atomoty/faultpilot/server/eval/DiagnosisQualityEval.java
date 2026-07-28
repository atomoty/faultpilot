package io.github.atomoty.faultpilot.server.eval;

import io.github.atomoty.faultpilot.adapters.mock.DemoFixtures;
import io.github.atomoty.faultpilot.adapters.mock.DemoScenario;
import io.github.atomoty.faultpilot.core.adapter.DiagnosisModel;
import io.github.atomoty.faultpilot.core.model.DiagnosisReport;
import io.github.atomoty.faultpilot.core.model.DiagnosisRequest;
import io.github.atomoty.faultpilot.server.service.DiagnosisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quality evaluation of the configured AI provider against scenarios with known-correct answers.
 *
 * <p>Unlike the rest of the suite this calls a <b>real model</b>, so it is excluded from the normal
 * build and only runs under the {@code eval} profile:
 *
 * <pre>
 *   export FAULTPILOT_AI_PROVIDER=openai-api OPENAI_API_KEY=sk-... OPENAI_MODEL=gpt-4o-mini
 *   mvn -pl faultpilot-server -am verify -Peval
 * </pre>
 *
 * <p>Evidence still comes from the deterministic mock fixtures, so the scenarios are reproducible
 * and the model is the only variable. Run it after changing the prompt to see whether answer
 * quality moved. Each scenario is graded by {@link ReportScorer}; the run fails if any scenario
 * fails, and the score card is printed either way.
 */
@SpringBootTest(properties = "faultpilot.ingestion.require-token=false")
class DiagnosisQualityEval {

    private static final List<EvalResult> RESULTS = new ArrayList<>();

    @Autowired
    DiagnosisService diagnosisService;
    @Autowired
    DiagnosisModel model;

    @Test
    void everyScenarioIsDiagnosedCorrectly() {
        assertThat(model.name())
                .as("run the eval against a real provider, not the deterministic mock "
                        + "(set FAULTPILOT_AI_PROVIDER=openai-api or codex-cli)")
                .isNotEqualTo("mock");

        for (DemoScenario scenario : DemoFixtures.all()) {
            RESULTS.add(evaluate(scenario));
        }

        List<String> failed = RESULTS.stream().filter(r -> !r.passed()).map(EvalResult::scenario).toList();
        assertThat(failed).as("scenarios whose diagnosis was wrong — see the score card above").isEmpty();
    }

    private EvalResult evaluate(DemoScenario scenario) {
        DiagnosisRequest request = new DiagnosisRequest(
                scenario.projectId(), scenario.environment(),
                "系统出了什么问题?请根据证据说明根因。",
                DemoFixtures.ANCHOR.minus(Duration.ofHours(2)), DemoFixtures.ANCHOR);

        long start = System.currentTimeMillis();
        DiagnosisReport report = diagnosisService.diagnose(request);
        return ReportScorer.score(scenario, report, System.currentTimeMillis() - start);
    }

    @AfterAll
    static void printScoreCard() {
        if (RESULTS.isEmpty()) {
            return;
        }
        long passed = RESULTS.stream().filter(EvalResult::passed).count();
        StringBuilder out = new StringBuilder("\n=== FaultPilot diagnosis quality eval ===\n");
        RESULTS.forEach(r -> out.append(r.format()).append('\n'));
        out.append("-----------------------------------------\n")
                .append("%d/%d scenarios passed%n".formatted(passed, RESULTS.size()));
        System.out.println(out);
    }
}
