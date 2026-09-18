package com.codewalnut.prreviewer.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codewalnut.prreviewer.config.OpenAiProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Verifies OpenAiClient's request shape and error translation against a real
 * local HTTP server, not a mocked RestClient -- same approach as
 * GitHubClientTest, so the actual wire behavior is what's under test.
 */
class OpenAiClientTest {

    private static HttpServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", OpenAiClientTest::route);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private OpenAiClient clientWithKey(String apiKey) {
        OpenAiProperties properties =
                new OpenAiProperties("http://localhost:" + server.getAddress().getPort(), "gpt-4o", apiKey);
        // Explicitly the JDK HttpClient factory (what production actually uses, since
        // Apache HttpClient5 is test-scope only): RestClient.builder() would otherwise
        // auto-select Apache HttpClient5 because it's on the test classpath, which
        // negotiates "Expect: 100-continue" for POST bodies -- something the JDK's
        // lightweight com.sun.net.httpserver.HttpServer test double handles badly,
        // adding a multi-second stall per request that has nothing to do with the
        // code under test.
        RestClient.Builder builder = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory());
        return new OpenAiClient(builder, properties);
    }

    private static void route(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        String path = exchange.getRequestURI().getPath();

        if (!"/chat/completions".equals(path)) {
            respond(exchange, 404, "{\"error\":\"unknown path\"}");
            return;
        }
        if ("Bearer bad-key".equals(auth)) {
            respond(exchange, 401, "{\"error\":{\"message\":\"Incorrect API key provided\"}}");
            return;
        }
        if ("Bearer rate-limited".equals(auth)) {
            respondWithHeaders(
                    exchange, 429, "{\"error\":{\"message\":\"Rate limit exceeded\"}}", Map.of("Retry-After", "20"));
            return;
        }
        if ("Bearer server-error".equals(auth)) {
            respond(exchange, 500, "{\"error\":{\"message\":\"Internal error\"}}");
            return;
        }
        if ("Bearer no-choices".equals(auth)) {
            respond(exchange, 200, "{\"choices\":[]}");
            return;
        }

        respond(
                exchange,
                200,
                """
                {"id":"chatcmpl-1","choices":[
                  {"index":0,"message":{"role":"assistant","content":"{\\"findings\\":[]}"},"finish_reason":"stop"}
                ]}""");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respondWithHeaders(exchange, status, body, Map.of());
    }

    private static void respondWithHeaders(
            HttpExchange exchange, int status, String body, Map<String, String> headers) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void returnsAssistantMessageContentOnSuccess() {
        String content = clientWithKey("good-key").chatCompletion("system", "user");
        assertThat(content).isEqualTo("{\"findings\":[]}");
    }

    @Test
    void throwsAuthorizationExceptionWhenApiKeyBlank() {
        assertThatThrownBy(() -> clientWithKey("").chatCompletion("system", "user"))
                .isInstanceOf(OpenAiAuthorizationException.class);
        assertThatThrownBy(() -> clientWithKey(null).chatCompletion("system", "user"))
                .isInstanceOf(OpenAiAuthorizationException.class);
    }

    @Test
    void translates401ToAuthorizationException() {
        assertThatThrownBy(() -> clientWithKey("bad-key").chatCompletion("system", "user"))
                .isInstanceOf(OpenAiAuthorizationException.class);
    }

    @Test
    void translatesRateLimitedResponseToRateLimitException() {
        assertThatThrownBy(() -> clientWithKey("rate-limited").chatCompletion("system", "user"))
                .isInstanceOf(OpenAiRateLimitException.class)
                .satisfies(ex -> assertThat(((OpenAiRateLimitException) ex).getRetryAfterSeconds()).isEqualTo(20L));
    }

    @Test
    void translates500ToApiException() {
        assertThatThrownBy(() -> clientWithKey("server-error").chatCompletion("system", "user"))
                .isInstanceOf(OpenAiApiException.class);
    }

    @Test
    void throwsApiExceptionWhenNoChoicesReturned() {
        assertThatThrownBy(() -> clientWithKey("no-choices").chatCompletion("system", "user"))
                .isInstanceOf(OpenAiApiException.class);
    }
}
