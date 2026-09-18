package com.codewalnut.prreviewer.llm;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Severity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the raw JSON string OpenAiClient returns into LlmFindings, failing
 * loudly (LlmResponseParseException) rather than silently accepting a
 * malformed/garbage response or half-populated finding -- per SPEC's "the
 * review fails explicitly" boundary rather than a partial result presented
 * as complete.
 *
 * filePath is a parameter, not read from the model's response -- see
 * ReviewPromptBuilder for why the model is never asked to echo it back.
 */
public final class LlmResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmResponseParser() {}

    public static List<LlmFinding> parse(String rawContent, String filePath) {
        if (rawContent == null || rawContent.isBlank()) {
            throw new LlmResponseParseException("OpenAI returned an empty response body");
        }

        ResponseBody body;
        try {
            body = MAPPER.readValue(rawContent, ResponseBody.class);
        } catch (Exception e) {
            throw new LlmResponseParseException(
                    "OpenAI response was not the expected JSON shape: " + e.getMessage(), e);
        }
        if (body.findings() == null) {
            throw new LlmResponseParseException("OpenAI response JSON had no 'findings' array");
        }

        List<LlmFinding> findings = new ArrayList<>();
        for (RawFinding raw : body.findings()) {
            if (raw.category() == null || raw.severity() == null || raw.message() == null || raw.message().isBlank()) {
                throw new LlmResponseParseException(
                        "OpenAI finding was missing a required field (category/severity/message): " + raw);
            }
            findings.add(
                    new LlmFinding(
                            raw.practiceCode(),
                            raw.category(),
                            raw.severity(),
                            filePath,
                            raw.lineStart(),
                            raw.lineEnd(),
                            raw.message()));
        }
        return findings;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResponseBody(List<RawFinding> findings) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawFinding(
            Integer lineStart,
            Integer lineEnd,
            Category category,
            Severity severity,
            String message,
            String practiceCode) {}
}
