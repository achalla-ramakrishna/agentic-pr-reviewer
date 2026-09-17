package com.codewalnut.prreviewer.client;

/** What a GitHub URL resolved to: a specific PR, or a whole repo at some ref. */
public sealed interface GitHubTarget {

    record PullRequest(String owner, String repo, int number) implements GitHubTarget {}

    record Repository(String owner, String repo, String ref) implements GitHubTarget {}
}
