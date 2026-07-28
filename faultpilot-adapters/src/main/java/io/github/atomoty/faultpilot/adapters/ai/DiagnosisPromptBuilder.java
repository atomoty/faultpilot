package io.github.atomoty.faultpilot.adapters.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.core.model.DiagnosisContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the system and user prompts shared by all real model providers from a (already sanitized)
 * {@link DiagnosisContext}. The system prompt encodes the product's guardrails; the user prompt is
 * the structured evidence as compact JSON so the model can reference evidence ids precisely.
 */
public class DiagnosisPromptBuilder {

    public static final String SYSTEM_PROMPT = """
            You are a read-only troubleshooting assistant for Java applications.
            Rules:
            - Explain ONLY the evidence provided. Never invent root causes or facts not in the evidence.
            - The input `ruleCandidates` were derived deterministically from the evidence. Write one
              output candidate for each of them, copying its `label` verbatim; your job is to explain
              them, not to select among them. If you think the evidence only shows correlation rather
              than cause, keep the candidate and say so plainly in its `explanation`.
            - Do not invent candidates whose label is absent from `ruleCandidates`; they are discarded.
            - Every root-cause candidate must reference one or more of the provided evidence ids. If
              the evidence is too thin to explain anything, say so in the summary.
            - Do NOT output any confidence score or probability (evidence strength is computed elsewhere).
            - Return output strictly matching the provided JSON schema, in the language of the question.
            """;

    private final ObjectMapper mapper;

    public DiagnosisPromptBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    /** Compact JSON of the question and each evidence collection (ids included). */
    public String userPrompt(DiagnosisContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", context.request().question());
        payload.put("from", context.request().from());
        payload.put("to", context.request().to());
        payload.put("logClusters", context.logClusters());
        payload.put("metricAnomalies", context.metricAnomalies());
        payload.put("slowSqlSummaries", context.slowSqlSummaries());
        payload.put("changeEvents", context.changeEvents());
        payload.put("databaseHealth", context.databaseHealth());
        payload.put("timeline", context.timeline());
        payload.put("ruleCandidates", context.ruleCandidates());
        payload.put("unavailableSources", context.unavailableSources());
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Context is plain records; serialization should not fail. Surface as unavailable.
            throw new ModelUnavailableException("Failed to serialize diagnosis context", e);
        }
    }
}
