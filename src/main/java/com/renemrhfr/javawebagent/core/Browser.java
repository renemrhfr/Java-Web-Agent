package com.renemrhfr.javawebagent.core;

import com.renemrhfr.javawebagent.BrowserLib;
import com.renemrhfr.javawebagent.api.BrowseJobResult;
import com.renemrhfr.javawebagent.config.BrowserConfig;
import com.renemrhfr.javawebagent.config.Prompts;
import com.renemrhfr.javawebagent.snapshot.SnapshotResult;
import org.slf4j.LoggerFactory;
import org.springaicommunity.replicate.ReplicateChatModel;
import org.springaicommunity.replicate.ReplicateChatOptions;
import org.springaicommunity.replicate.api.ReplicateApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;

/**
 * LLM-driven browser automation service.
 *
 * <p>Orchestrates a multi-step loop where an LLM inspects accessibility
 * snapshots, decides on browser actions, and the service executes them
 * via {@link BrowserLib}. Supports navigation, clicking, typing, form
 * submission, link extraction, and note-taking across steps.</p>
 *
 */
@Service
public final class Browser {

    private static final Logger logger = LoggerFactory.getLogger(Browser.class.getName());
    private static final int MAX_LINKS = 50;
    private static final int MAX_LINK_TEXT_LENGTH = 120;
    private static final int MAX_LINK_HREF_LENGTH = 300;

    private final int maxSteps;
    private final int maxConsecutiveFailures;
    private final String cookiePath;
    private final boolean screenshotPerStep;
    private final ReplicateChatModel chatModel;
    private final BrowserConfig config;

    public Browser(ReplicateApi replicateApi, @Value("${browser.max-steps:20}") int maxSteps,
                   @Value("${browser.max-consecutive-failures:8}") int maxConsecutiveFailures,
                   @Value("${browser.headless:true}") boolean headless,
                   @Value("${browser.low-resource-mode:true}") boolean lowResourceMode,
                   @Value("${browser.timeout-ms:25000}") int timeoutMs,
                   @Value("${browser.snapshot.max-chars:5000}") int snapshotMaxChars,
                   @Value("${browser.snapshot.include-details:false}") boolean snapshotIncludeDetails,
                   @Value("${browser.snapshot.filter-visible-only:true}") boolean snapshotFilterVisibleOnly,
                   @Value("${browser.block-resources.images:true}") boolean blockImages,
                   @Value("${browser.block-resources.fonts:true}") boolean blockFonts,
                   @Value("${browser.block-resources.media:true}") boolean blockMedia,
                   @Value("${browser.llm.model:google/gemini-3-flash}") String llmModel,
                   @Value("${browser.cookie-path:}") String cookiePath,
                   @Value("${browser.screenshot.per-step:false}") boolean screenshotPerStep,
                   @Value("${browser.executable-path:}") String executablePath) {
        this.maxSteps = maxSteps;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.cookiePath = cookiePath;
        this.screenshotPerStep = screenshotPerStep;
        ReplicateChatOptions options = ReplicateChatOptions.builder().model(llmModel).build();
        this.chatModel = ReplicateChatModel.builder().replicateApi(replicateApi).defaultOptions(options).build();
        this.config = BrowserConfig.builder().headless(headless).lowResourceMode(lowResourceMode).timeoutMs(timeoutMs).snapshotMaxChars(snapshotMaxChars).snapshotIncludeDetails(snapshotIncludeDetails).snapshotFilterVisibleOnly(snapshotFilterVisibleOnly).blockImages(blockImages).blockFonts(blockFonts).blockMedia(blockMedia).executablePath(executablePath).build();
        logger.info("Browser initialized");
    }


    /**
     * Runs the LLM-driven browser automation loop for the given task.
     *
     * <p>Creates a headless browser, feeds accessibility snapshots to the
     * LLM, and executes the returned actions until the LLM reports
     * completion, the step limit is reached, or too many consecutive
     * failures occur.</p>
     *
     * @param task a natural-language description of the browsing objective
     * @return the result containing status, message, trace, and final snapshot
     */
    public BrowseJobResult run(String task) {
        logger.info("Browser.run() started with task: " + task);
        List<String> trace = new ArrayList<>();
        List<String> stepScreenshots = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> failedActionSignatures = new LinkedHashSet<>();
        int failures = 0;
        try {
            logger.info("Creating BrowserLib...");
            long browserStartTime = System.nanoTime();
            BrowserLib browser = BrowserLib.create(this.config);
            long browserCreatedTime = System.nanoTime();
            logger.info("BrowserLib created successfully in " + (browserCreatedTime - browserStartTime) / 1_000_000 + "ms");
            try {
                if (cookiePath != null && !cookiePath.isBlank()) {
                    java.nio.file.Path path = java.nio.file.Path.of(cookiePath);
                    if (java.nio.file.Files.exists(path)) {
                        logger.info("Importing cookies from " + path);
                        browser.importCookies(path);
                        logger.info("Cookies imported");
                    }
                }
                for (int step = 1; step <= maxSteps; step++) {
                    logger.info("Step " + step + " starting");
                    SnapshotResult snapshot = browser.currentSnapshot();
                    logger.info("Step " + step + " snapshot taken, calling LLM...");
                    String prompt = buildPrompt(task, step, browser.page().url(), snapshot.text(), trace, notes);
                    String llmResponse = chatModel.call(prompt);
                    logger.info("Step " + step + " LLM response received");
                    LlmDecision decision = LlmDecisionParser.parse(llmResponse);
                    trace.add("step " + step + " | llm: " + llmResponse);

                    if ("done".equals(decision.status())) {
                        logger.info("Step " + step + " - done status received");
                        logger.info(decision.message());
                        String finalScreenshot = toBase64(browser.screenshot());
                        return new BrowseJobResult("success", decision.message(), step, trace, browser.currentSnapshot().text(), finalScreenshot, stepScreenshots);
                    }

                    if ("note".equals(decision.status())) {
                        String noteText = decision.note() != null ? decision.note() : decision.message();
                        if (noteText != null && !noteText.isBlank()) {
                            notes.add(noteText);
                        }
                        trace.add("step " + step + " | noted: " + oneLine(noteText));
                        continue;
                    }

                    if ("action".equals(decision.status()) && "extract_links".equals(decision.kind())) {
                        logger.info("Step " + step + " - extracting links from page");
                        String links = extractLinks(browser);
                        trace.add("step " + step + " | extracted_links:\n" + links);
                        notes.add("LINKS:\n" + links);
                        continue;
                    }

                    try {
                        logger.info("Step " + step + " applying action...");
                        String signature = actionSignature(decision);
                        if (!signature.isBlank() && failedActionSignatures.contains(signature)) {
                            throw new IllegalArgumentException("repeated failed action blocked: " + signature);
                        }
                        applyDecision(decision, browser);
                        failures = 0;
                        if (screenshotPerStep) {
                            stepScreenshots.add(toBase64(browser.screenshot()));
                        }
                        logger.info("Step " + step + " action applied successfully");
                    } catch (Exception e) {
                        failures += 1;
                        logger.warn("Step " + step + " action failed: " + e.getMessage());
                        String signature = actionSignature(decision);
                        if (!signature.isBlank()) {
                            failedActionSignatures.add(signature);
                            if (failedActionSignatures.size() > 40) {
                                String first = failedActionSignatures.iterator().next();
                                failedActionSignatures.remove(first);
                            }
                        }
                        trace.add("step " + step + " | action_failed: " + compactError(e));
                        if (failures >= maxConsecutiveFailures) {
                            logger.warn("Too many consecutive failures, aborting");
                            String finalScreenshot = toBase64(browser.screenshot());
                            return new BrowseJobResult("failed", "Too many consecutive action failures.", step, trace, browser.currentSnapshot().text(), finalScreenshot, stepScreenshots);
                        }
                    }
                }
                logger.info("Max steps reached, returning snapshot");
                String finalScreenshot = toBase64(browser.screenshot());
                return new BrowseJobResult("incomplete", "Max steps reached. Returning current snapshot as fallback.", maxSteps, trace, browser.currentSnapshot().text(), finalScreenshot, stepScreenshots);
            } finally {
                logger.info("Closing BrowserLib");
                browser.close();
            }
        } catch (Exception e) {
            logger.error("Browser.run() failed with exception: " + e.getMessage());
            throw new RuntimeException("Browser automation failed", e);
        }
    }

    private void applyDecision(LlmDecision decision, BrowserLib browser) {
        if (!"action".equals(decision.status())) {
            throw new IllegalArgumentException("unsupported status: " + decision.status());
        }
        String kind = required(decision.kind(), "kind is required for action");
        switch (kind) {
            case "navigate" -> {
                String url = required(decision.url(), "url is required for navigate");
                browser.navigate(url);
            }
            case "click" ->
                    browser.act(new ActRequest("click", required(decision.ref(), "ref is required"), null, null, null, null, null, null, null, null));
            case "type" -> {
                boolean submit = Boolean.TRUE.equals(decision.submit());
                browser.act(new ActRequest("type", required(decision.ref(), "ref is required"), null, null, required(decision.text(), "text is required"), null, null, null, null, Map.of("submit", submit)));
            }
            case "submit" ->
                    browser.act(new ActRequest("submit", required(decision.ref(), "ref is required"), null, null, null, null, null, null, null, null));
            case "press" ->
                    browser.act(new ActRequest("press", null, null, null, null, required(decision.key(), "key is required for press"), null, null, null, null));
            case "select" ->
                    browser.act(new ActRequest("select", required(decision.ref(), "ref is required"), null, null, null, null, null, null, requiredValues(decision.values(), "values are required for select"), null));
            default -> throw new IllegalArgumentException("unsupported action kind: " + kind);
        }
    }

    private static String buildPrompt(String task, int step, String currentUrl, String snapshotText, List<String> trace, List<String> notes) {
        String history = recentHistory(trace, 6);
        String notesBlock = notes.isEmpty() ? "(none)" : String.join("\n", notes);
        return Prompts.SYSTEM_PROMPT + "\n" + "TASK:\n" + task + "\n\n" + "STEP: " + step + "\n" + "<<<CURRENT_URL>>>\n" + currentUrl + "\n<<<END_CURRENT_URL>>>\n" + "<<<NOTES>>>\n" + notesBlock + "\n<<<END_NOTES>>>\n" + "<<<RECENT_HISTORY>>>\n" + history + "\n<<<END_RECENT_HISTORY>>>\n" + "<<<SNAPSHOT>>>\n" + snapshotText + "\n<<<END_SNAPSHOT>>>\n";
    }

    private static String extractLinks(BrowserLib browser) {
        try {
            Object result = browser.page().evaluate("""
                        () => {
                            const links = [];
                            const seen = new Set();
                            for (const a of document.querySelectorAll('a[href]')) {
                                const href = a.href;
                                if (!href || href.startsWith('javascript:') || seen.has(href)) continue;
                                seen.add(href);
                                const text = (a.textContent || '').replace(/\\s+/g, ' ').trim();
                                if (!text) continue;
                                links.push({ text: text.substring(0, %d), href: href.substring(0, %d) });
                                if (links.length >= %d) break;
                            }
                            return links;
                        }
                    """.formatted(MAX_LINK_TEXT_LENGTH, MAX_LINK_HREF_LENGTH, MAX_LINKS));
            if (!(result instanceof List<?> list) || list.isEmpty()) {
                return "(no links found)";
            }
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object textObj = map.get("text");
                    Object hrefObj = map.get("href");
                    String text = textObj != null ? String.valueOf(textObj) : "";
                    String href = hrefObj != null ? String.valueOf(hrefObj) : "";
                    sb.append("- ").append(text).append(" -> ").append(href).append("\n");
                }
            }
            return sb.isEmpty() ? "(no links found)" : sb.toString().trim();
        } catch (Exception e) {
            return "(failed to extract links: " + e.getMessage() + ")";
        }
    }

    private static String recentHistory(List<String> trace, int maxLines) {
        if (trace == null || trace.isEmpty()) {
            return "(none)";
        }
        int from = Math.max(0, trace.size() - maxLines);
        return trace.subList(from, trace.size()).stream().map(Browser::oneLine).collect(Collectors.joining("\n"));
    }

    private static String compactError(Exception e) {
        return oneLine(e == null ? "" : String.valueOf(e.getMessage()));
    }

    private static String oneLine(String text) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.length() > 280) {
            return flat.substring(0, 280) + "...";
        }
        return flat;
    }

    private static String actionSignature(LlmDecision decision) {
        if (decision == null) {
            return "";
        }
        String kind = decision.kind() == null ? "" : decision.kind().trim().toLowerCase();
        return switch (kind) {
            case "navigate" -> "navigate|" + oneLine(decision.url());
            case "click", "submit" -> kind + "|" + oneLine(decision.ref());
            case "type" -> "type|" + oneLine(decision.ref()) + "|" + oneLine(decision.text()) + "|submit=" + Boolean.TRUE.equals(decision.submit());
            case "press" -> "press|" + oneLine(decision.key());
            case "select" -> "select|" + oneLine(decision.ref()) + "|" + normalizeValues(decision.values());
            default -> "";
        };
    }

    private static String normalizeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream().map(Browser::oneLine).collect(Collectors.joining(",", "[", "]"));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static List<String> requiredValues(List<String> values, String message) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        List<String> out = values.stream().filter(v -> v != null && !v.isBlank()).toList();
        if (out.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return out;
    }

    private static String toBase64(byte[] png) {
        return java.util.Base64.getEncoder().encodeToString(png);
    }
}
