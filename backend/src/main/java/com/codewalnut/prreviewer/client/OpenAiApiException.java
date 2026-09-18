package com.codewalnut.prreviewer.client;

/** OpenAI is unreachable, returned a server error, or returned an unusable response. Retryable. */
public class OpenAiApiException extends RuntimeException {

    public OpenAiApiException(String message) {
        super(message);
    }

    public OpenAiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
