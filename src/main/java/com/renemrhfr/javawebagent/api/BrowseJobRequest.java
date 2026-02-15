package com.renemrhfr.javawebagent.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the browse endpoint.
 *
 * @param prompt the natural-language browsing task
 *
 */
public record BrowseJobRequest(@NotBlank String prompt) {}
