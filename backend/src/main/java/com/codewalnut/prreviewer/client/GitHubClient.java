package com.codewalnut.prreviewer.client;

import com.codewalnut.prreviewer.config.GitHubProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over the GitHub REST API. Only calls read endpoints — never
 * write/delete — per the least-privilege guardrail in AGENTS.md. Everything
 * this returns (file paths, patch text, file content) is untrusted data from
 * a reviewed repository, not instructions.
 */
@Component
public class GitHubClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 3; // caps PR files at 300, GitHub's own documented max

    private final RestClient restClient;

    public GitHubClient(RestClient.Builder builder, GitHubProperties properties) {
        this.restClient = builder.baseUrl(properties.apiBaseUrl()).build();
    }

    public RepositoryMetadata fetchRepository(String owner, String repo, String token) {
        return execute(
                spec ->
                        spec.get()
                                .uri("/repos/{owner}/{repo}", owner, repo)
                                .headers(h -> applyAuth(h, token))
                                .retrieve()
                                .body(RepositoryMetadata.class),
                owner + "/" + repo);
    }

    public PullRequestMetadata fetchPullRequest(String owner, String repo, int number, String token) {
        return execute(
                spec ->
                        spec.get()
                                .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, number)
                                .headers(h -> applyAuth(h, token))
                                .retrieve()
                                .body(PullRequestMetadata.class),
                owner + "/" + repo + "#" + number);
    }

    public List<ChangedFile> fetchPullRequestFiles(String owner, String repo, int number, String token) {
        List<ChangedFile> files = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            final int currentPage = page;
            PullRequestFile[] batch =
                    execute(
                            spec ->
                                    spec.get()
                                            .uri(
                                                    "/repos/{owner}/{repo}/pulls/{number}/files?per_page={size}&page={page}",
                                                    owner,
                                                    repo,
                                                    number,
                                                    PAGE_SIZE,
                                                    currentPage)
                                            .headers(h -> applyAuth(h, token))
                                            .retrieve()
                                            .body(PullRequestFile[].class),
                            owner + "/" + repo + "#" + number);
            if (batch == null || batch.length == 0) {
                break;
            }
            for (PullRequestFile f : batch) {
                files.add(f.toChangedFile());
            }
            if (batch.length < PAGE_SIZE) {
                break;
            }
        }
        return files;
    }

    public GitTree fetchTree(String owner, String repo, String sha, String token) {
        return execute(
                spec ->
                        spec.get()
                                .uri("/repos/{owner}/{repo}/git/trees/{sha}?recursive=1", owner, repo, sha)
                                .headers(h -> applyAuth(h, token))
                                .retrieve()
                                .body(GitTree.class),
                owner + "/" + repo + "@" + sha);
    }

    public String fetchFileContent(String owner, String repo, String path, String ref, String token) {
        ContentResponse response =
                execute(
                        spec ->
                                spec.get()
                                        .uri(
                                                "/repos/{owner}/{repo}/contents/{path}?ref={ref}",
                                                owner,
                                                repo,
                                                path,
                                                ref)
                                        .headers(h -> applyAuth(h, token))
                                        .retrieve()
                                        .body(ContentResponse.class),
                        owner + "/" + repo + "/" + path);
        if (response == null || response.content() == null) {
            return "";
        }
        byte[] decoded = Base64.getMimeDecoder().decode(response.content());
        return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void applyAuth(org.springframework.http.HttpHeaders headers, String token) {
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
    }

    private <T> T execute(java.util.function.Function<RestClient, T> call, String targetDescription) {
        try {
            return call.apply(restClient);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw translateClientError(e, targetDescription);
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            throw new GitHubApiException(
                    "GitHub returned a server error for " + targetDescription + ": " + e.getStatusCode(), e);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new GitHubApiException("Could not reach GitHub for " + targetDescription, e);
        }
    }

    private RuntimeException translateClientError(
            org.springframework.web.client.HttpClientErrorException e, String targetDescription) {
        HttpStatusCode status = e.getStatusCode();
        if (status.value() == 404) {
            return new GitHubNotFoundException(
                    "Not found (or token can't see it): " + targetDescription);
        }
        if (status.value() == 401) {
            return new GitHubAuthorizationException("GitHub token was rejected for " + targetDescription);
        }
        if (status.value() == 403 || status.value() == 429) {
            String remaining = e.getResponseHeaders() == null
                    ? null
                    : e.getResponseHeaders().getFirst("X-RateLimit-Remaining");
            if ("0".equals(remaining) || status.value() == 429) {
                String retryAfter =
                        e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
                Long seconds = retryAfter == null ? null : Long.valueOf(retryAfter);
                return new GitHubRateLimitException(
                        "GitHub rate limit hit while fetching " + targetDescription, seconds);
            }
            return new GitHubAuthorizationException(
                    "Token lacks access to " + targetDescription + " (403)");
        }
        return new GitHubApiException(
                "GitHub returned " + status + " for " + targetDescription, e);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryMetadata(String name, String full_name, String default_branch) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequestMetadata(int number, String title, PrRef head, PrRef base) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrRef(String sha, String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequestFile(
            String filename,
            String previous_filename,
            String status,
            String patch,
            int additions,
            int deletions) {

        ChangedFile toChangedFile() {
            return new ChangedFile(
                    filename,
                    previous_filename,
                    mapStatus(status),
                    patch,
                    null,
                    additions,
                    deletions);
        }

        private static ChangedFileStatus mapStatus(String githubStatus) {
            return switch (githubStatus) {
                case "added" -> ChangedFileStatus.ADDED;
                case "removed" -> ChangedFileStatus.REMOVED;
                case "renamed" -> ChangedFileStatus.RENAMED;
                default -> ChangedFileStatus.MODIFIED;
            };
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitTree(String sha, List<GitTreeEntry> tree, boolean truncated) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitTreeEntry(String path, String type, Long size) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentResponse(String content, String encoding) {}
}
