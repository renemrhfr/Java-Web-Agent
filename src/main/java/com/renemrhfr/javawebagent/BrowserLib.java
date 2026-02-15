package com.renemrhfr.javawebagent;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import com.renemrhfr.javawebagent.actions.BrowserActions;
import com.renemrhfr.javawebagent.actions.StorageActions;
import com.renemrhfr.javawebagent.config.BrowserConfig;
import com.renemrhfr.javawebagent.core.ActRequest;
import com.renemrhfr.javawebagent.core.RoleRef;
import com.renemrhfr.javawebagent.session.PlaywrightSession;
import com.renemrhfr.javawebagent.snapshot.SemanticSnapshot;
import com.renemrhfr.javawebagent.snapshot.SnapshotResult;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level browser automation facade built on Playwright.
 *
 * <p>Provides navigation, element interaction (click, type, fill, hover,
 * submit, drag, select), tab management, cookie/storage operations, and
 * accessibility-based snapshots. Actions are resolved via ARIA role refs
 * and automatically retried after a fresh snapshot on failure.</p>
 *
 */
public final class BrowserLib implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BrowserLib.class.getName());

    private static final int MIN_ACTION_TIMEOUT_MS = 1_500;
    private static final int MAX_ACTION_TIMEOUT_MS = 4_000;

    private final BrowserConfig config;
    private final PlaywrightSession session;
    private final BrowserActions actions;
    private final StorageActions storage;
    private final SemanticSnapshot snapshots;
    private SnapshotResult lastSnapshot;

    private BrowserLib(BrowserConfig config) {
        this.config = config;
        this.session = new PlaywrightSession(config);
        this.actions = new BrowserActions();
        this.storage = new StorageActions();
        this.snapshots = new SemanticSnapshot();
        this.lastSnapshot = snapshots.capture(page(), config);
    }

    /**
     * Creates a new {@code BrowserLib} instance with the given configuration.
     *
     * @param config the browser configuration
     * @return a ready-to-use browser instance
     */
    public static BrowserLib create(BrowserConfig config) {
        return new BrowserLib(config);
    }

    /**
     * Returns the currently active Playwright {@link Page}.
     */
    public Page page() {
        return session.activePage();
    }

    /**
     * Returns the stable page ID for the given page.
     *
     * @param page the page to identify
     * @return a unique identifier string
     */
    public String pageId(Page page) {
        return PlaywrightSession.pageId(page);
    }

    /**
     * Navigates the active page to the specified URL and refreshes the snapshot.
     *
     * @param url the URL to navigate to
     */
    public void navigate(String url) {
        actions.navigate(page(), url, config.timeoutMs());
        refreshSnapshot();
    }

    /**
     * Takes a fresh accessibility snapshot of the current page.
     *
     * @return the snapshot result containing text and element refs
     */
    public SnapshotResult snapshot() {
        refreshSnapshot();
        return lastSnapshot;
    }

    /**
     * Returns the most recently captured snapshot without refreshing.
     */
    public SnapshotResult currentSnapshot() {
        return lastSnapshot;
    }

    /**
     * Takes a viewport screenshot of the current page.
     */
    public byte[] screenshot() {
        return screenshot(false);
    }

    /**
     * Takes a screenshot of the current page.
     *
     * @param fullPage whether to capture the full scrollable page
     * @return PNG image bytes
     */
    public byte[] screenshot(boolean fullPage) {
        return actions.screenshot(page(), fullPage);
    }

    /**
     * Clicks the element identified by the given ref, retrying with a fresh
     * snapshot if the first attempt fails.
     *
     * @param ref the element reference (e.g. "e1")
     */
    public void click(String ref) {
        RoleRef target = requireRef(ref);
        log("click:start ref=" + ref + " role=" + target.role() + " name='" + target.name() + "' url=" + safeUrl());
        try {
            actions.click(page(), target, actionTimeoutMs());
        } catch (RuntimeException firstFailure) {
            log("click:retry reason=" + firstFailure.getClass().getSimpleName() + ": " + firstFailure.getMessage());
            refreshSnapshot();
            RoleRef recovered = recoverRef(target);
            if (recovered == null) {
                log("click:failed no_recovery");
                throw firstFailure;
            }
            actions.click(page(), recovered, actionTimeoutMs());
        }
        refreshSnapshot();
        log("click:done ref=" + ref + " url=" + safeUrl());
    }

    /**
     * Types text into the element identified by the given ref.
     *
     * @param ref  the element reference
     * @param text the text to type
     */
    public void type(String ref, String text) {
        type(ref, text, false);
    }

    /**
     * Types text into the element identified by the given ref, optionally
     * submitting afterwards.
     *
     * @param ref    the element reference
     * @param text   the text to type
     * @param submit whether to press Enter after typing
     */
    public void type(String ref, String text, boolean submit) {
        RoleRef roleRef = requireRef(ref);
        log("type:start ref=" + ref + " role=" + roleRef.role() + " name='" + roleRef.name() + "' submit=" + submit);
        try {
            actions.type(page(), roleRef, text, actionTimeoutMs(), submit);
        } catch (RuntimeException firstFailure) {
            log("type:retry reason=" + firstFailure.getClass().getSimpleName() + ": " + firstFailure.getMessage());
            refreshSnapshot();
            RoleRef recovered = recoverRef(roleRef);
            if (recovered == null) {
                throw firstFailure;
            }
            actions.type(page(), recovered, text, actionTimeoutMs(), submit);
        }
        refreshSnapshot();
        log("type:done ref=" + ref);
    }

    /**
     * Fills the element identified by the given ref with a value.
     *
     * @param ref   the element reference
     * @param value the value to fill
     */
    public void fill(String ref, String value) {
        RoleRef roleRef = requireRef(ref);
        log("fill:start ref=" + ref + " role=" + roleRef.role() + " name='" + roleRef.name() + "'");
        try {
            actions.fill(page(), roleRef, value, actionTimeoutMs());
        } catch (RuntimeException firstFailure) {
            log("fill:retry reason=" + firstFailure.getClass().getSimpleName() + ": " + firstFailure.getMessage());
            refreshSnapshot();
            RoleRef recovered = recoverRef(roleRef);
            if (recovered == null) {
                throw firstFailure;
            }
            actions.fill(page(), recovered, value, actionTimeoutMs());
        }
        refreshSnapshot();
        log("fill:done ref=" + ref);
    }

    /**
     * Hovers over the element identified by the given ref.
     *
     * @param ref the element reference
     */
    public void hover(String ref) {
        RoleRef roleRef = requireRef(ref);
        log("hover:start ref=" + ref + " role=" + roleRef.role() + " name='" + roleRef.name() + "'");
        actions.hover(page(), roleRef, actionTimeoutMs());
        log("hover:done ref=" + ref);
    }

    /**
     * Presses a keyboard key globally.
     *
     * @param key the key to press (e.g. "Enter", "Tab")
     */
    public void press(String key) {
        actions.press(page(), key);
        refreshSnapshot();
    }

    /**
     * Submits the form associated with the element identified by the given ref.
     *
     * @param ref the element reference
     */
    public void submit(String ref) {
        RoleRef roleRef = requireRef(ref);
        log("submit:start ref=" + ref + " role=" + roleRef.role() + " name='" + roleRef.name() + "'");
        try {
            actions.submit(page(), roleRef, actionTimeoutMs());
        } catch (RuntimeException firstFailure) {
            log("submit:retry reason=" + firstFailure.getClass().getSimpleName() + ": " + firstFailure.getMessage());
            refreshSnapshot();
            RoleRef recovered = recoverRef(roleRef);
            if (recovered == null) {
                throw firstFailure;
            }
            actions.submit(page(), recovered, actionTimeoutMs());
        }
        refreshSnapshot();
        log("submit:done ref=" + ref);
    }

    /**
     * Drags one element onto another.
     *
     * @param startRef the ref of the element to drag
     * @param endRef   the ref of the drop target
     */
    public void drag(String startRef, String endRef) {
        actions.drag(page(), requireRef(startRef), requireRef(endRef), actionTimeoutMs());
        refreshSnapshot();
    }

    /**
     * Selects one or more options in a dropdown or listbox.
     *
     * @param ref    the element reference
     * @param values the option texts to select
     */
    public void select(String ref, List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values are required");
        }
        RoleRef roleRef = requireRef(ref);
        try {
            actions.select(page(), roleRef, values, actionTimeoutMs());
        } catch (RuntimeException firstFailure) {
            log("select:retry reason=" + firstFailure.getClass().getSimpleName() + ": " + firstFailure.getMessage());
            refreshSnapshot();
            RoleRef recovered = recoverRef(roleRef);
            if (recovered == null) {
                throw firstFailure;
            }
            actions.select(page(), recovered, values, actionTimeoutMs());
        }
        refreshSnapshot();
    }

    /**
     * Returns all cookies from the current browser context.
     */
    public List<Cookie> getCookies() {
        return storage.getCookies(session.context());
    }

    /**
     * Adds the given cookies to the current browser context.
     */
    public void setCookies(List<Cookie> cookies) {
        storage.setCookies(session.context(), cookies);
    }

    /**
     * Clears all cookies from the current browser context.
     */
    public void clearCookies() {
        storage.clearCookies(session.context());
    }

    /**
     * Imports cookies from a JSON or Netscape-format file.
     *
     * @param file the cookie file to import
     */
    public void importCookies(Path file) {
        storage.importCookies(session.context(), file);
    }

    /**
     * Reads a value from browser storage.
     *
     * @param kind "local" or "session"
     * @param key  the storage key
     * @return the stored value, or {@code null} if absent
     */
    public String getStorage(String kind, String key) {
        return storage.getStorage(page(), kind, key);
    }

    /**
     * Writes a value to browser storage.
     *
     * @param kind  "local" or "session"
     * @param key   the storage key
     * @param value the value to store
     */
    public void setStorage(String kind, String key, String value) {
        storage.setStorage(page(), kind, key, value);
    }

    /**
     * Clears all entries from the specified browser storage.
     *
     * @param kind "local" or "session"
     */
    public void clearStorage(String kind) {
        storage.clearStorage(page(), kind);
    }

    /**
     * Executes a browser action described by the given request.
     *
     * @param req the action request
     */
    public void act(ActRequest req) {
        if (req == null || req.kind() == null) {
            throw new IllegalArgumentException("kind is required");
        }
        switch (req.kind()) {
            case "navigate" -> navigate(require(req.url(), "url is required"));
            case "click" -> click(require(req.ref(), "ref is required"));
            case "hover" -> hover(require(req.ref(), "ref is required"));
            case "type" -> type(require(req.ref(), "ref is required"), require(req.text(), "text is required"), optionBoolean(req.options(), "submit"));
            case "fill" -> fill(require(req.ref(), "ref is required"), req.value() == null ? "" : req.value());
            case "press" -> press(require(req.key(), "key is required"));
            case "submit" -> submit(require(req.ref(), "ref is required"));
            case "drag" -> drag(require(req.startRef(), "startRef is required"), require(req.endRef(), "endRef is required"));
            case "select" -> select(require(req.ref(), "ref is required"), req.values() == null ? List.of() : req.values());
            default -> throw new IllegalArgumentException("unsupported kind: " + req.kind());
        }
    }

    private RoleRef requireRef(String ref) {
        Map<String, RoleRef> refs = lastSnapshot.refs();
        RoleRef found = refs.get(ref);
        if (found == null) {
            throw new IllegalArgumentException("unknown ref: " + ref);
        }
        return found;
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static boolean optionBoolean(Map<String, Object> options, String key) {
        if (options == null) {
            return false;
        }
        Object value = options.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return "true".equalsIgnoreCase(s.trim());
        }
        return false;
    }

    private RoleRef recoverRef(RoleRef target) {
        String targetName = normalize(target.name());
        String targetRole = normalize(target.role());
        return lastSnapshot.refs().values().stream().filter(ref -> normalize(ref.role()).equals(targetRole) && normalize(ref.name()).equals(targetName)).findFirst().orElseGet(() -> lastSnapshot.refs().values().stream().filter(ref -> normalize(ref.name()).equals(targetName)).min(Comparator.comparingInt(ref -> roleDistance(targetRole, normalize(ref.role())))).orElse(null));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static int roleDistance(String expected, String candidate) {
        if (expected.equals(candidate)) {
            return 0;
        }
        if (expected.isBlank() || candidate.isBlank()) {
            return 2;
        }
        return 1;
    }

    private int actionTimeoutMs() {
        return Math.max(MIN_ACTION_TIMEOUT_MS, Math.min(config.timeoutMs(), MAX_ACTION_TIMEOUT_MS));
    }

    private void log(String message) {
        logger.info(message);
    }

    private String safeUrl() {
        try {
            return page().url();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private void refreshSnapshot() {
        Page p = page();
        try {
            p.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(600));
            if (!config.lowResourceMode()) {
                p.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(600));
            }
        } catch (Exception ignored) {
            // network may not go idle (SSE, polling, etc.) — ignore
        }
        lastSnapshot = snapshots.capture(p, config);
    }

    @Override
    public void close() {
        session.close();
    }
}
