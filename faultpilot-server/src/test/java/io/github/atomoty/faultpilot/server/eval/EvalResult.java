package io.github.atomoty.faultpilot.server.eval;

import java.util.List;

/**
 * Score card for one evaluated scenario. Each check is a hard pass/fail on the produced report;
 * {@link #passed()} is true only when every check passed.
 *
 * @param scenario           project/environment under test
 * @param rootCauseHit       the report contains the expected root-cause label
 * @param evidenceCited      the winning candidate cites every required evidence id
 * @param noFabricatedIds    no candidate references an evidence id absent from the report
 * @param modelAnswered      the model produced the report (not a rule-only fallback)
 * @param summaryPresent     the summary is non-blank
 * @param notes              human-readable detail for whatever failed
 * @param actualLabels       root-cause labels the report actually returned
 * @param elapsedMillis      wall-clock time of the diagnosis call
 */
public record EvalResult(
        String scenario,
        boolean rootCauseHit,
        boolean evidenceCited,
        boolean noFabricatedIds,
        boolean modelAnswered,
        boolean summaryPresent,
        List<String> notes,
        List<String> actualLabels,
        long elapsedMillis
) {

    public boolean passed() {
        return rootCauseHit && evidenceCited && noFabricatedIds && modelAnswered && summaryPresent;
    }

    /** One-line summary for the console report. */
    public String format() {
        return "%-34s %s  [root=%s evidence=%s no-fabrication=%s model=%s summary=%s] %dms%s"
                .formatted(scenario, passed() ? "PASS" : "FAIL",
                        mark(rootCauseHit), mark(evidenceCited), mark(noFabricatedIds),
                        mark(modelAnswered), mark(summaryPresent), elapsedMillis,
                        notes.isEmpty() ? "" : "\n    " + String.join("\n    ", notes));
    }

    private static String mark(boolean ok) {
        return ok ? "ok" : "NO";
    }
}
