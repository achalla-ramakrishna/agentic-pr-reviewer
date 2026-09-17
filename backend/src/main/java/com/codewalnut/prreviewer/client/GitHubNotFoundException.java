package com.codewalnut.prreviewer.client;

/** The repo/PR doesn't exist, or the caller's token can't see it — GitHub returns 404 either way. */
public class GitHubNotFoundException extends RuntimeException {

    public GitHubNotFoundException(String message) {
        super(message);
    }
}
