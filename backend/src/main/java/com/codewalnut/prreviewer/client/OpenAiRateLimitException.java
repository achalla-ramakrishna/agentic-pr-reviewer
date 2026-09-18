package com.codewalnut.prreviewer.client;

/** OpenAI's rate limit was hit. retryAfterSeconds is null when OpenAI didn't say how long to wait. */
public class OpenAiRateLimitException extends RuntimeException {

    private final Long retryAfterSeconds;

    public OpenAiRateLimitException(String message, Long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
