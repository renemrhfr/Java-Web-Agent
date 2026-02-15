package com.renemrhfr.javawebagent.core;

import java.util.List;
import java.util.Map;

/**
 * Describes a single browser action to execute.
 *
 * <p>The {@code kind} field determines which action is performed (e.g.
 * "click", "type", "navigate"), and the remaining fields provide
 * action-specific parameters.</p>
 *

 */
public record ActRequest(
    String kind,
    String ref,
    String startRef,
    String endRef,
    String text,
    String key,
    String url,
    String value,
    List<String> values,
    Map<String, Object> options
) {}
