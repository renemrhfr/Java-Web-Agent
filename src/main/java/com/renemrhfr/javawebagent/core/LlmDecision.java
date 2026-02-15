package com.renemrhfr.javawebagent.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Parsed LLM response representing a single browser planning decision.
 *
 * <p>The {@code status} field determines the decision type: "action",
 * "note", or "done". Action-specific fields (ref, url, text, etc.) are
 * populated based on the action kind.</p>
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmDecision(
    String status,
    String kind,
    String ref,
    String url,
    String text,
    Boolean submit,
    String key,
    List<String> values,
    String note,
    String message
) {}
