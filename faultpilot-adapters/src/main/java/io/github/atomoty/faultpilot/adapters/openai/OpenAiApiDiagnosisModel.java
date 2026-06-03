package io.github.atomoty.faultpilot.adapters.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.atomoty.faultpilot.adapters.ai.DiagnosisPromptBuilder;
import io.github.atomoty.faultpilot.adapters.ai.ModelOutputParser;
import io.github.atomoty.faultpilot.adapters.ai.ModelUnavailableException;
import io.github.atomoty.faultpilot.adapters.ai.OutputSchema;
import io.github.atomoty.faultpilot.core.adapter.DiagnosisModel;
import io.github.atomoty.faultpilot.core.model.DiagnosisContext;
import io.github.atomoty.faultpilot.core.model.ModelOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

/**
 * {@link DiagnosisModel} backed by the OpenAI chat completions API (design §20.4). Sends the
 * sanitized context with a strict JSON-schema response format and parses the model's content into a
 * {@link ModelOutput}. Any transport error, non-success status, timeout, or malformed output throws
 * {@link ModelUnavailableException} so the service falls back to a rule-only report.
 *
 * <p>The API key is sent in the Authorization header only; it is never logged.
 */
public class OpenAiApiDiagnosisModel implements DiagnosisModel {

    private static final Logger log = LoggerFactory.getLogger(OpenAiApiDiagnosisModel.class);

    private final OpenAiConfig config;
    private final HttpInvoker http;
    private final ObjectMapper mapper;
    private final DiagnosisPromptBuilder prompts;
    private final ModelOutputParser parser;

    public OpenAiApiDiagnosisModel(OpenAiConfig config, HttpInvoker http, ObjectMapper mapper) {
        this.config = config;
        this.http = http;
        this.mapper = mapper;
        this.prompts = new DiagnosisPromptBuilder(mapper);
        this.parser = new ModelOutputParser(mapper);
    }

    @Override
    public String name() {
        return "openai-api";
    }

    @Override
    public ModelOutput generate(DiagnosisContext context) {
        String requestBody = buildRequestBody(context);
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.chatCompletionsUrl()))
                .timeout(config.timeout())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request);
        } catch (HttpTimeoutException e) {
            throw new ModelUnavailableException("OpenAI request timed out", e);
        } catch (IOException e) {
            throw new ModelUnavailableException("OpenAI request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelUnavailableException("OpenAI request interrupted", e);
        }

        if (response.statusCode() / 100 != 2) {
            // Do not log the body verbatim (may echo prompt content); status is enough.
            log.warn("OpenAI returned HTTP {}", response.statusCode());
            throw new ModelUnavailableException("OpenAI returned HTTP " + response.statusCode());
        }
        return parser.parse(extractContent(response.body()));
    }

    private String buildRequestBody(DiagnosisContext context) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", config.model());

        ArrayNode messages = root.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", prompts.systemPrompt());
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompts.userPrompt(context));

        // Strict JSON schema response format so the content is parseable into ModelOutput.
        ObjectNode responseFormat = root.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", "faultpilot_diagnosis");
        jsonSchema.put("strict", true);
        try {
            jsonSchema.set("schema", mapper.readTree(OutputSchema.JSON));
            return mapper.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ModelUnavailableException("Failed to build OpenAI request", e);
        }
    }

    private String extractContent(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new ModelUnavailableException("OpenAI response had no choices");
            }
            return choices.get(0).path("message").path("content").asText("");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ModelUnavailableException("OpenAI response was not valid JSON", e);
        }
    }
}
