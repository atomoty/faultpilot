package io.github.atomoty.faultpilot.adapters.ai;

/**
 * The JSON Schema the model must produce, shared by all real model providers. It mirrors
 * {@link io.github.atomoty.faultpilot.core.model.ModelOutput}: a summary, root-cause candidates
 * (each referencing existing evidence ids), and recommended actions. No self-rated probability field
 * is allowed (specification.md §3, §10).
 */
public final class OutputSchema {

    private OutputSchema() {
    }

    /** Compact JSON Schema string (strict) for the model output. */
    public static final String JSON = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["summary", "rootCauseCandidates", "recommendedActions"],
              "properties": {
                "summary": { "type": "string" },
                "rootCauseCandidates": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["label", "title", "explanation", "evidenceIds"],
                    "properties": {
                      "label": { "type": "string" },
                      "title": { "type": "string" },
                      "explanation": { "type": "string" },
                      "evidenceIds": { "type": "array", "items": { "type": "string" } }
                    }
                  }
                },
                "recommendedActions": { "type": "array", "items": { "type": "string" } }
              }
            }
            """;
}
