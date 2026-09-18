package com.codewalnut.prreviewer.client;

/** The configured OPENAI_API_KEY is missing, invalid, or lacks access. An operator problem, not the caller's. */
public class OpenAiAuthorizationException extends RuntimeException {

    public OpenAiAuthorizationException(String message) {
        super(message);
    }
}
