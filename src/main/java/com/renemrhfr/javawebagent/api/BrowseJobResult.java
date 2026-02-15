package com.renemrhfr.javawebagent.api;

import java.util.List;

/**
 * Result of a browser automation run, including the outcome status,
 * step count, execution trace, and the final page snapshot.
 *
 * @param status          "success", "failed", or "incomplete"
 * @param reason          human-readable summary of the outcome
 * @param steps           number of steps executed
 * @param trace           step-by-step execution log
 * @param finalSnapshot   the accessibility snapshot at completion
 * @param finalScreenshot base64-encoded PNG screenshot of the page at completion
 * @param stepScreenshots base64-encoded PNG screenshots after each successful action (empty when per-step screenshots are disabled)
 *
 */
public record BrowseJobResult(String status, String reason, int steps, List<String> trace, String finalSnapshot,
                              String finalScreenshot, List<String> stepScreenshots) {
}
