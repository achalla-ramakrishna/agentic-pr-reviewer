package com.codewalnut.prreviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** apiKey is read from the OPENAI_API_KEY env var (see application.yml) -- never committed. */
@ConfigurationProperties(prefix = "pr-reviewer.openai")
public record OpenAiProperties(String apiBaseUrl, String model, String apiKey) {}
