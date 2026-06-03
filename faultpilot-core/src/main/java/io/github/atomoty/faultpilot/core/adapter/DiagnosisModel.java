package io.github.atomoty.faultpilot.core.adapter;

import io.github.atomoty.faultpilot.core.model.DiagnosisContext;
import io.github.atomoty.faultpilot.core.model.ModelOutput;

/**
 * Generates a diagnosis narrative from structured context. See design.md §20.1.
 * Implementations: Mock, OpenAI API, and experimental local Codex CLI.
 */
public interface DiagnosisModel {

    /** A short name used for logging and provider selection (e.g. "mock", "openai-api"). */
    String name();

    /**
     * Produce the model's contribution: summary, root-cause titles/explanations and actions.
     * May throw if the model is unavailable; callers fall back to a rule-only report.
     */
    ModelOutput generate(DiagnosisContext context);
}
