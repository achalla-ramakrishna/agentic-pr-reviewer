package com.codewalnut.prreviewer.client;

/** GitHub is unreachable, or returned a server error. Retryable. */
public class GitHubApiException extends RuntimeException {

    public GitHubApiException(String message) {
        super(message);
    }

    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
