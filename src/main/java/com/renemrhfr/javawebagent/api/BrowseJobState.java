package com.renemrhfr.javawebagent.api;

import java.time.Instant;

/**
 * Current State of a Job
 * @param jobId         Job-ID
 * @param status        Current Status
 * @param submittedAt   -
 * @param startedAt     -
 * @param finishedAt    -
 * @param error         -
 * @param result        Detailed Result
 */
record BrowseJobState(String jobId, BrowseJobStatus status, Instant submittedAt, Instant startedAt, Instant finishedAt,
                      String error, BrowseJobResult result) {
    BrowseJobState withStatus(BrowseJobStatus next, Instant started, Instant finished, BrowseJobResult nextResult) {
        return new BrowseJobState(jobId, next, submittedAt, started, finished, error, nextResult);
    }

    BrowseJobState withError(Instant finished, String nextError) {
        return new BrowseJobState(jobId, BrowseJobStatus.FAILED, submittedAt, startedAt, finished, nextError, null);
    }
}
