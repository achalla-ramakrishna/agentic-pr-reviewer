package com.codewalnut.prreviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pr-reviewer.github")
public record GitHubProperties(String apiBaseUrl) {}
