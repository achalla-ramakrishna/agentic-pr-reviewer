package com.codewalnut.prreviewer.llm;

/** OpenAI's response wasn't the JSON shape the system prompt specified. */
public class LlmResponseParseException extends RuntimeException {

    public LlmResponseParseException(String message) {
        super(message);
    }

    public LlmResponseParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
