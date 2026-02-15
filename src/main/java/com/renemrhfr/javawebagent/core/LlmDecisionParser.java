package com.renemrhfr.javawebagent.core;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses raw LLM JSON responses into {@link LlmDecision} instances.
 *
 */
public final class LlmDecisionParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmDecisionParser() {
    }

    public static LlmDecision parse(String raw) {
        try {
            var sanitized = sanitizeJsonResponse(raw);
            return MAPPER.readValue(sanitized, LlmDecision.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid LLM response: " + raw, e);
        }
    }

    /**
     * Sanitizes response by removing markdown code blocks.
     * Useful when expecting JSON responses.
     */
    private static String sanitizeJsonResponse(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        if (response.contains("```json")) {
            response = response.replace("```json", "").replace("```", "");
        }
        return response.replace("\n", "");
    }
}
