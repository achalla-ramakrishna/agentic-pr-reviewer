package com.codewalnut.prreviewer.client;

/** The token is missing, invalid, or lacks the scope needed for this repo. */
public class GitHubAuthorizationException extends RuntimeException {

    public GitHubAuthorizationException(String message) {
        super(message);
    }
}
