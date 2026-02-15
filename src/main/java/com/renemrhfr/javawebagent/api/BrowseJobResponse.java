package com.renemrhfr.javawebagent.api;

import java.time.Instant;

/**
 * The final Job Response
 * @param jobId         ID
 * @param status        {@link BrowseJobStatus}
 * @param submittedAt   Submitted Time
 * @param startedAt     Started time
 * @param finishedAt    Finish Time
 * @param error         Info about Errors, if any occured
 * @param result        Detailed Result Info
 */
record BrowseJobResponse(String jobId, String status, Instant submittedAt, Instant startedAt, Instant finishedAt,
                         String error, BrowseJobResult result) {
}
