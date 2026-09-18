package com.codewalnut.prreviewer.client;

import com.codewalnut.prreviewer.config.OpenAiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over the OpenAI Chat Completions API. The API key is a
 * server-side secret (OPENAI_API_KEY, see OpenAiProperties) rather than a
 * per-request value like the GitHub token -- callers never supply it. Only
 * ever sends what LlmReviewService builds (one file's diff/content plus
 * retrieved practice context) -- never a whole-repo dump -- per the
 * least-privilege guardrail in AGENTS.md.
 */
@Component
public class OpenAiClient {

    private final RestClient restClient;
    private final String model;
    private final String apiKey;

    public OpenAiClient(RestClient.Builder builder, OpenAiProperties properties) {
        this.restClient = builder.baseUrl(properties.apiBaseUrl()).build();
        this.model = properties.model();
        this.apiKey = properties.apiKey();
    }

    /** Returns the assistant's raw message content (expected to be a JSON string per the system prompt). */
    public String chatCompletion(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAiAuthorizationException("OPENAI_API_KEY is not configured");
        }

        ChatRequest request =
                new ChatRequest(
                        model,
                        List.of(new ChatMessage("system", systemPrompt), new ChatMessage("user", userPrompt)),
                        new ResponseFormat("json_object"),
                        0.0);

        ChatResponse response =
                execute(
                        spec ->
                                spec.post()
                                        .uri("/chat/completions")
                                        .headers(h -> h.setBearerAuth(apiKey))
                                        .body(request)
                                        .retrieve()
                                        .body(ChatResponse.class));

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new OpenAiApiException("OpenAI response contained no choices");
        }
        return response.choices().get(0).message().content();
    }

    private <T> T execute(Function<RestClient, T> call) {
        try {
            return call.apply(restClient);
        } catch (HttpClientErrorException e) {
            throw translateClientError(e);
        } catch (HttpServerErrorException e) {
            throw new OpenAiApiException("OpenAI returned a server error: " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            throw new OpenAiApiException("Could not reach OpenAI", e);
        }
    }

    private RuntimeException translateClientError(HttpClientErrorException e) {
        HttpStatusCode status = e.getStatusCode();
        if (status.value() == 401) {
            return new OpenAiAuthorizationException("OpenAI rejected the configured API key (401)");
        }
        if (status.value() == 429) {
            String retryAfter =
                    e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
            Long seconds = retryAfter == null ? null : Long.valueOf(retryAfter);
            return new OpenAiRateLimitException("OpenAI rate limit exceeded", seconds);
        }
        if (status.value() == 403) {
            return new OpenAiAuthorizationException("OpenAI request forbidden (403): " + e.getMessage());
        }
        return new OpenAiApiException("OpenAI returned " + status + ": " + e.getMessage(), e);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatRequest(String model, List<ChatMessage> messages, ResponseFormat response_format, double temperature) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatMessage(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResponseFormat(String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(ChatMessage message) {}
}
