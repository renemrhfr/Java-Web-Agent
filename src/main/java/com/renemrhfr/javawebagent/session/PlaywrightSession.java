package com.renemrhfr.javawebagent.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.renemrhfr.javawebagent.config.BrowserConfig;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages a Playwright browser session including the browser instance,
 * context, and tab lifecycle.
 *
 * <p>Wraps Chromium launch configuration, request-blocking rules, and
 * multi-tab management behind a single {@link AutoCloseable} resource.</p>
 *
 */
public final class PlaywrightSession implements AutoCloseable {
    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;

    /**
     * Creates a new Playwright session using the supplied configuration.
     *
     * @param config browser configuration controlling headless mode, timeouts,
     *               and resource-blocking rules
     */
    public PlaywrightSession(BrowserConfig config) {
        this.playwright = Playwright.create();

        BrowserType chromium = playwright.chromium();
        BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions()
                .setHeadless(config.headless())
                .setChromiumSandbox(false)
                .setArgs(chromiumArgs(config));

        // Only set executable path if provided
        if (config.executablePath() != null && !config.executablePath().isBlank()) {
            launch.setExecutablePath(Paths.get(config.executablePath()));
        }

        this.browser = chromium.launch(launch);
        this.context = browser.newContext();
        setupRequestBlocking(context, config);

        context.setDefaultTimeout(config.timeoutMs());

        if (context.pages().isEmpty()) {
            context.newPage();
        }
    }

    /**
     * Returns the underlying Playwright {@link Browser} instance.
     */
    public Browser browser() {
        return browser;
    }

    /**
     * Returns the current {@link BrowserContext}.
     */
    public BrowserContext context() {
        return context;
    }

    /**
     * Returns the currently active (last opened) page, creating one if none exist.
     *
     * @return the active {@link Page}
     */
    public Page activePage() {
        List<Page> pages = context.pages();
        if (pages.isEmpty()) {
            return context.newPage();
        }
        return pages.get(pages.size() - 1);
    }

    /**
     * Returns a stable, unique ID for a page by injecting a persistent
     * JavaScript variable.
     *
     * @param page the page to identify
     * @return a UUID string unique to this page
     */
    public static String pageId(Page page) {
        try {
            Object id = page.evaluate("() => window.__browserTabId || (window.__browserTabId = crypto.randomUUID())");
            if (id instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (RuntimeException ignored) {
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public void close() {
        context.close();
        browser.close();
        playwright.close();
    }

    private static List<String> chromiumArgs(BrowserConfig config) {
        List<String> args = new ArrayList<>();
        args.add("--disable-background-networking");
        args.add("--disable-backgrounding-occluded-windows");
        args.add("--disable-breakpad");
        args.add("--disable-component-update");
        args.add("--disable-default-apps");
        args.add("--disable-dev-shm-usage");
        args.add("--disable-extensions");
        args.add("--disable-features=BackForwardCache,Translate,MediaRouter,OptimizationHints");
        args.add("--disable-sync");
        args.add("--metrics-recording-only");
        args.add("--mute-audio");
        args.add("--no-default-browser-check");
        args.add("--no-first-run");
        args.add("--password-store=basic");
        args.add("--use-mock-keychain");
        if (config.lowResourceMode()) {
            args.add("--blink-settings=imagesEnabled=false");
        }
        return args;
    }

    private static void setupRequestBlocking(BrowserContext context, BrowserConfig config) {
        if (!config.blockImages() && !config.blockFonts() && !config.blockMedia()) {
            return;
        }
        context.route("**/*", route -> {
            String type = route.request().resourceType();
            boolean blockImage = config.blockImages() && "image".equals(type);
            boolean blockFont = config.blockFonts() && "font".equals(type);
            boolean blockMedia = config.blockMedia() && "media".equals(type);
            if (blockImage || blockFont || blockMedia) {
                route.abort();
                return;
            }
            route.resume();
        });
    }
}
