package io.github.atomoty.faultpilot.adapters.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.core.model.ModelOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the model's JSON output (conforming to {@link OutputSchema}) into a {@link ModelOutput}.
 * Malformed or empty output throws {@link ModelUnavailableException} so the caller can fall back.
 */
public class ModelOutputParser {

    private final ObjectMapper mapper;

    public ModelOutputParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ModelOutput parse(String json) {
        if (json == null || json.isBlank()) {
            throw new ModelUnavailableException("Model returned empty output");
        }
        try {
            JsonNode root = mapper.readTree(json);
            // Output must be a JSON object with the required "summary" string (per OutputSchema);
            // structurally-wrong-but-valid JSON (array, bare value, unrelated object) is unusable.
            if (root == null || !root.isObject() || !root.path("summary").isTextual()) {
                throw new ModelUnavailableException("Model output did not match the expected schema");
            }
            String summary = root.path("summary").asText("");
            List<ModelOutput.Candidate> candidates = new ArrayList<>();
            for (JsonNode c : root.path("rootCauseCandidates")) {
                String label = c.path("label").asText("");
                if (label.isBlank()) {
                    continue; // a candidate with no label cannot be matched to a rule candidate
                }
                List<String> evidenceIds = new ArrayList<>();
                for (JsonNode id : c.path("evidenceIds")) {
                    evidenceIds.add(id.asText());
                }
                candidates.add(new ModelOutput.Candidate(
                        label,
                        c.path("title").asText(""),
                        c.path("explanation").asText(""),
                        evidenceIds));
            }
            List<String> actions = new ArrayList<>();
            for (JsonNode a : root.path("recommendedActions")) {
                actions.add(a.asText());
            }
            return new ModelOutput(summary, candidates, actions);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ModelUnavailableException("Model returned invalid JSON", e);
        }
    }
}
