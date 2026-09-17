package com.codewalnut.prreviewer.client;

/** GitHub's rate limit was hit. retryAfterSeconds is null when GitHub didn't say how long to wait. */
public class GitHubRateLimitException extends RuntimeException {

    private final Long retryAfterSeconds;

    public GitHubRateLimitException(String message, Long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
