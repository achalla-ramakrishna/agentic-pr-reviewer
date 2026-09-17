package com.codewalnut.prreviewer.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

/**
 * Parses a GitHub repo or PR URL into a {@link GitHubTarget}. Pure, no
 * network calls — resolving whether a target actually exists (and whether
 * the caller's token can see it) happens in GitHubService.
 */
public final class GitHubUrlParser {

    private GitHubUrlParser() {}

    public static GitHubTarget parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be blank");
        }

        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Not a valid URL: " + url, e);
        }

        String host = uri.getHost();
        if (host == null || !host.equalsIgnoreCase("github.com") && !host.equalsIgnoreCase("www.github.com")) {
            throw new IllegalArgumentException("Not a github.com URL: " + url);
        }

        List<String> segments =
                Arrays.stream(uri.getPath().split("/")).filter(s -> !s.isBlank()).toList();

        if (segments.size() < 2) {
            throw new IllegalArgumentException("URL must include an owner and repo: " + url);
        }

        String owner = segments.get(0);
        String repo = stripGitSuffix(segments.get(1));

        if (segments.size() >= 4 && "pull".equals(segments.get(2))) {
            int number;
            try {
                number = Integer.parseInt(segments.get(3));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid PR number in URL: " + url, e);
            }
            return new GitHubTarget.PullRequest(owner, repo, number);
        }

        if (segments.size() >= 4 && "tree".equals(segments.get(2))) {
            String ref = String.join("/", segments.subList(3, segments.size()));
            return new GitHubTarget.Repository(owner, repo, ref);
        }

        // Bare repo URL — caller resolves the default branch.
        return new GitHubTarget.Repository(owner, repo, null);
    }

    private static String stripGitSuffix(String repo) {
        return repo.endsWith(".git") ? repo.substring(0, repo.length() - 4) : repo;
    }
}
