package com.renemrhfr.javawebagent.api;

/**
 * Response for Browser Request.
 *
 * @param jobId
 * @param status
 * @param message
 */
record BrowseAcceptedResponse(String jobId, String status, String message) {
}
