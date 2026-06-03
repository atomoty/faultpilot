package io.github.atomoty.faultpilot.adapters.openai;

import java.time.Duration;

/**
 * Resolved OpenAI connection configuration. Built by the server from {@code faultpilot.ai.*}.
 *
 * @param baseUrl API base, e.g. {@code https://api.openai.com}
 * @param apiKey  bearer key injected from the environment (never logged)
 * @param model   chat model id
 * @param timeout connect/read timeout for the request
 */
public record OpenAiConfig(String baseUrl, String apiKey, String model, Duration timeout) {

    public OpenAiConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("faultpilot.ai.base-url must be set");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("faultpilot.ai.api-key (OPENAI_API_KEY) must be set for openai-api");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("faultpilot.ai.model (OPENAI_MODEL) must be set for openai-api");
        }
        timeout = timeout == null ? Duration.ofSeconds(35) : timeout;
    }

    /** The chat completions endpoint, with any trailing slash on baseUrl trimmed. */
    public String chatCompletionsUrl() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/v1/chat/completions";
    }

    /** Masks the API key so the record is never accidentally logged with the secret (review P2). */
    @Override
    public String toString() {
        return "OpenAiConfig[baseUrl=" + baseUrl + ", apiKey=***, model=" + model + ", timeout=" + timeout + "]";
    }
}
