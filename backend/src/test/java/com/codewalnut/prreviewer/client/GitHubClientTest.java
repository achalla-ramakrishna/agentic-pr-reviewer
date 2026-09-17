package com.codewalnut.prreviewer.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codewalnut.prreviewer.config.GitHubProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Verifies GitHubClient's request shape and, more importantly, that it
 * translates GitHub's HTTP error responses into the right typed exception —
 * against a real local HTTP server, not a mocked RestClient, so the actual
 * wire behavior (status codes, headers) is what's under test.
 */
class GitHubClientTest {

    private static HttpServer server;
    private static GitHubClient client;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", GitHubClientTest::route);
        server.start();

        GitHubProperties properties = new GitHubProperties("http://localhost:" + server.getAddress().getPort());
        client = new GitHubClient(RestClient.builder(), properties);
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        if (path.equals("/repos/octocat/Hello-World")) {
            respond(exchange, 200, """
                    {"name":"Hello-World","full_name":"octocat/Hello-World","default_branch":"main"}""");
        } else if (path.equals("/repos/octocat/Hello-World/pulls/1")) {
            respond(
                    exchange,
                    200,
                    """
                    {"number":1,"title":"Test PR",
                     "head":{"sha":"abc123","ref":"feature"},
                     "base":{"sha":"def456","ref":"main"}}""");
        } else if (path.equals("/repos/octocat/Hello-World/pulls/1/files")) {
            if (query != null && query.contains("page=2")) {
                respond(exchange, 200, "[]");
            } else {
                respond(
                        exchange,
                        200,
                        """
                        [{"filename":"src/Main.java","status":"modified","patch":"@@ -1 +1 @@\\n-old\\n+new",
                          "additions":1,"deletions":1},
                         {"filename":"src/New.java","status":"added","patch":"@@ -0,0 +1 @@\\n+new file",
                          "additions":1,"deletions":0}]""");
            }
        } else if (path.equals("/repos/octocat/Hello-World/git/trees/main")) {
            respond(
                    exchange,
                    200,
                    """
                    {"sha":"main","truncated":false,
                     "tree":[{"path":"README.md","type":"blob","size":100},
                             {"path":"src","type":"tree"}]}""");
        } else if (path.equals("/repos/octocat/Hello-World/contents/README.md")) {
            String base64 = java.util.Base64.getEncoder().encodeToString("hello world".getBytes(StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"content\":\"" + base64 + "\",\"encoding\":\"base64\"}");
        } else if (path.equals("/repos/missing/repo")) {
            respond(exchange, 404, "{\"message\":\"Not Found\"}");
        } else if (path.equals("/repos/bad-token/repo")) {
            respond(exchange, 401, "{\"message\":\"Bad credentials\"}");
        } else if (path.equals("/repos/rate-limited/repo")) {
            respondWithHeaders(
                    exchange, 403, "{\"message\":\"API rate limit exceeded\"}",
                    Map.of("X-RateLimit-Remaining", "0", "Retry-After", "30"));
        } else if (path.equals("/repos/insufficient-scope/repo")) {
            respond(exchange, 403, "{\"message\":\"Resource not accessible\"}");
        } else if (path.equals("/repos/server-error/repo")) {
            respond(exchange, 500, "{\"message\":\"Internal error\"}");
        } else {
            respond(exchange, 404, "{\"message\":\"unhandled test path: " + path + "\"}");
        }
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
    void fetchesRepositoryMetadata() {
        GitHubClient.RepositoryMetadata repo = client.fetchRepository("octocat", "Hello-World", null);
        assertThat(repo.default_branch()).isEqualTo("main");
    }

    @Test
    void fetchesPullRequestMetadata() {
        GitHubClient.PullRequestMetadata pr = client.fetchPullRequest("octocat", "Hello-World", 1, null);
        assertThat(pr.head().sha()).isEqualTo("abc123");
    }

    @Test
    void fetchesPullRequestFilesAcrossPages() {
        List<ChangedFile> files = client.fetchPullRequestFiles("octocat", "Hello-World", 1, null);

        assertThat(files).hasSize(2);
        assertThat(files.get(0).path()).isEqualTo("src/Main.java");
        assertThat(files.get(0).status()).isEqualTo(ChangedFileStatus.MODIFIED);
        assertThat(files.get(1).status()).isEqualTo(ChangedFileStatus.ADDED);
    }

    @Test
    void fetchesTreeAndFileContent() {
        GitHubClient.GitTree tree = client.fetchTree("octocat", "Hello-World", "main", null);
        assertThat(tree.tree()).hasSize(2);

        String content = client.fetchFileContent("octocat", "Hello-World", "README.md", "main", null);
        assertThat(content).isEqualTo("hello world");
    }

    @Test
    void translates404ToNotFoundException() {
        assertThatThrownBy(() -> client.fetchRepository("missing", "repo", null))
                .isInstanceOf(GitHubNotFoundException.class);
    }

    @Test
    void translates401ToAuthorizationException() {
        assertThatThrownBy(() -> client.fetchRepository("bad-token", "repo", "bad-token-value"))
                .isInstanceOf(GitHubAuthorizationException.class);
    }

    @Test
    void translatesRateLimitedResponseToRateLimitException() {
        assertThatThrownBy(() -> client.fetchRepository("rate-limited", "repo", null))
                .isInstanceOf(GitHubRateLimitException.class)
                .satisfies(
                        ex -> assertThat(((GitHubRateLimitException) ex).getRetryAfterSeconds()).isEqualTo(30L));
    }

    @Test
    void translatesInsufficientScope403ToAuthorizationException() {
        assertThatThrownBy(() -> client.fetchRepository("insufficient-scope", "repo", null))
                .isInstanceOf(GitHubAuthorizationException.class);
    }

    @Test
    void translates500ToApiException() {
        assertThatThrownBy(() -> client.fetchRepository("server-error", "repo", null))
                .isInstanceOf(GitHubApiException.class);
    }
}
