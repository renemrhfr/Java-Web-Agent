package com.renemrhfr.javawebagent.api;

import com.renemrhfr.javawebagent.core.Browser;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.renemrhfr.javawebagent.api.BrowseJobStatus.SUCCEEDED;

/**
 * REST controller exposing the browser automation service.
 *
 * <p>Accepts browse requests via {@code POST /api/v1/browser/callagent}
 * and provides job status polling via {@code GET /api/v1/browser/callagent/{jobId}}.
 * Only one browse job may run at a time.</p>
 *
 */
@RestController
@RequestMapping(path = "/api/v1/browser", produces = MediaType.APPLICATION_JSON_VALUE)
@SuppressWarnings("unused")
public class BrowserController {

    private final Browser browser;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, BrowseJobState> jobs = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private volatile String activeJobId;
    private final ConfigurableApplicationContext context;

    /**
     * Creates a new browser controller.
     *
     * @param browser the browser automation service to delegate requests to
     */
    @SuppressWarnings("unused")
    public BrowserController(Browser browser, ConfigurableApplicationContext context) {
        this.browser = browser;
        this.context = context;
    }

    /**
     * Killswitch Endpoint in case the Browser gets stuck / hangs / does something harmful and you need an Option to remote close.
     * @return Response Ok.
     */
    @GetMapping(path = "/killswitch")
    public ResponseEntity<Void> killswitch() {
        Thread shutdownThread = new Thread(() -> {
            try {
                // Wait 1 sec so we can Respond
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(SpringApplication.exit(context, () -> 0));
        });

        shutdownThread.setDaemon(false);
        shutdownThread.start();
        return ResponseEntity.ok().build();
    }

    /**
     * Submits a new browse request for asynchronous execution.
     *
     * @param request the browse request containing the prompt
     * @return an accepted response with the job ID, or a conflict response
     * if a job is already running
     */
    @PostMapping(path = "/callagent", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BrowseAcceptedResponse> callagent(@RequestBody BrowseJobRequest request) {
        String jobId;
        // Only allow one Job for now
        synchronized (lock) {
            if (activeJobId != null) {
                BrowseJobState current = jobs.get(activeJobId);
                if (current != null && (current.status() == BrowseJobStatus.PENDING || current.status() == BrowseJobStatus.RUNNING)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new BrowseAcceptedResponse(activeJobId, current.status().name(), "A callagent job is already running. Only one concurrent request is allowed."));
                }
            }
            jobId = UUID.randomUUID().toString();
            activeJobId = jobId;
            jobs.put(jobId, new BrowseJobState(jobId, BrowseJobStatus.PENDING, Instant.now(), null, null, null, null));
        }
        final String submittedJobId = jobId;
        final String prompt = request.prompt();
        executor.submit(() -> runJob(submittedJobId, prompt));
        return ResponseEntity.accepted().body(new BrowseAcceptedResponse(submittedJobId, BrowseJobStatus.PENDING.name(), "Accepted"));
    }

    /**
     * Polls the status of a previously submitted browse job.
     *
     * @param jobId the job ID returned by the callagent endpoint
     * @return the current job state, or 404 if the job ID is unknown
     */
    @GetMapping(path = "/callagent/{jobId}")
    public ResponseEntity<BrowseJobResponse> callagentStatus(@PathVariable String jobId) {
        BrowseJobState state = jobs.get(jobId);
        if (state == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(new BrowseJobResponse(state.jobId(), state.status().name(), state.submittedAt(), state.startedAt(), state.finishedAt(), state.error(), state.result()));
    }

    /**
     * Shut down the executor service when the bean is destroyed.
     */
    @PreDestroy
    public void shutdownExecutor() {
        executor.shutdownNow();
    }

    /**
     * Executes a callagent job in the background.
     *
     * @param jobId  the unique job identifier
     * @param prompt the user prompt to process
     */
    private void runJob(String jobId, String prompt) {
        BrowseJobState pending = jobs.get(jobId);
        if (pending == null) {
            return;
        }
        jobs.put(jobId, pending.withStatus(BrowseJobStatus.RUNNING, Instant.now(), null, null));
        try {
            BrowseJobResult result = browser.run(prompt);
            BrowseJobState running = jobs.get(jobId);
            if (running != null) {
                jobs.put(jobId, running.withStatus(SUCCEEDED, running.startedAt(), Instant.now(), result));
            }
        } catch (Exception ex) {
            BrowseJobState running = jobs.get(jobId);
            if (running != null) {
                jobs.put(jobId, running.withError(Instant.now(), ex.getMessage()));
            }
        } finally {
            synchronized (lock) {
                if (jobId.equals(activeJobId)) {
                    activeJobId = null;
                }
            }
        }
    }
}

