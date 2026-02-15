package com.renemrhfr.javawebagent.actions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cookie and web-storage operations for a Playwright browser context.
 *
 * <p>Supports importing cookies from JSON and Netscape/Mozilla formats,
 * as well as reading and writing to {@code localStorage} and
 * {@code sessionStorage}.</p>
 *
 */
public final class StorageActions {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Returns all cookies from the browser context.
     */
    public List<Cookie> getCookies(BrowserContext context) {
        return context.cookies();
    }

    /**
     * Adds cookies to the browser context.
     */
    public void setCookies(BrowserContext context, List<Cookie> cookies) {
        context.addCookies(cookies);
    }

    /**
     * Clears all cookies from the browser context.
     */
    public void clearCookies(BrowserContext context) {
        context.clearCookies();
    }

    /**
     * Imports cookies from a file, auto-detecting JSON or Netscape format
     * by file extension.
     *
     * @param context the browser context to import into
     * @param file    the cookie file (JSON or Netscape format)
     */
    public void importCookies(BrowserContext context, Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".json")) {
            importCookiesFromJson(context, file);
            return;
        }
        importCookiesFromNetscape(context, file);
    }

    /**
     * Imports cookies from a JSON array file.
     *
     * @param context the browser context
     * @param file    path to the JSON cookie file
     */
    public void importCookiesFromJson(BrowserContext context, Path file) {
        try {
            List<Map<String, Object>> rows = mapper.readValue(file.toFile(), new TypeReference<>() {
            });
            List<Cookie> cookies = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String name = asString(row.get("name"));
                String value = asString(row.get("value"));
                String domain = asString(row.get("domain"));
                String path = asString(row.getOrDefault("path", "/"));
                if (name.isBlank() || value.isBlank()) {
                    continue;
                }
                Cookie cookie = new Cookie(name, value);
                if (!domain.isBlank()) {
                    cookie.setDomain(domain);
                }
                if (!path.isBlank()) {
                    cookie.setPath(path);
                }
                String url = asString(row.get("url"));
                if (!url.isBlank()) {
                    cookie.setUrl(url);
                }
                Number expires = asNumber(row.get("expires"));
                if (expires != null) {
                    cookie.setExpires(expires.doubleValue());
                }
                Boolean secure = asBoolean(row.get("secure"));
                if (secure != null) {
                    cookie.setSecure(secure);
                }
                Boolean httpOnly = asBoolean(row.get("httpOnly"));
                if (httpOnly != null) {
                    cookie.setHttpOnly(httpOnly);
                }
                String sameSite = asString(row.get("sameSite"));
                if (!sameSite.isBlank()) {
                    if ("Lax".equalsIgnoreCase(sameSite)) {
                        cookie.setSameSite(SameSiteAttribute.LAX);
                    } else if ("Strict".equalsIgnoreCase(sameSite)) {
                        cookie.setSameSite(SameSiteAttribute.STRICT);
                    } else if ("None".equalsIgnoreCase(sameSite)) {
                        cookie.setSameSite(SameSiteAttribute.NONE);
                    }
                }
                cookies.add(cookie);
            }
            context.addCookies(cookies);
        } catch (IOException e) {
            throw new RuntimeException("failed to import JSON cookies", e);
        }
    }

    /**
     * Imports cookies from a Netscape/Mozilla format file.
     *
     * @param context the browser context
     * @param file    path to the Netscape cookie file
     */
    public void importCookiesFromNetscape(BrowserContext context, Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            List<Cookie> cookies = new ArrayList<>();
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] cols = line.split("\\t");
                if (cols.length < 7) {
                    continue;
                }
                String domain = cols[0];
                String path = cols[2];
                boolean secure = "TRUE".equalsIgnoreCase(cols[3]);
                double expires = parseDouble(cols[4], 0);
                String name = cols[5];
                String value = cols[6];
                Cookie cookie = new Cookie(name, value).setDomain(domain).setPath(path).setSecure(secure);
                if (expires > 0) {
                    cookie.setExpires(expires);
                }
                cookies.add(cookie);
            }
            context.addCookies(cookies);
        } catch (IOException e) {
            throw new RuntimeException("failed to import Netscape cookies", e);
        }
    }

    /**
     * Reads a value from web storage.
     *
     * @param page the Playwright page
     * @param kind "session" for sessionStorage, anything else for localStorage
     * @param key  the storage key
     * @return the stored value
     */
    public String getStorage(Page page, String kind, String key) {
        String store = "session".equals(kind) ? "sessionStorage" : "localStorage";
        return (String) page.evaluate("([s, k]) => window[s].getItem(k)", List.of(store, key));
    }

    /**
     * Writes a value to web storage.
     *
     * @param page  the Playwright page
     * @param kind  "session" for sessionStorage, anything else for localStorage
     * @param key   the storage key
     * @param value the value to store
     */
    public void setStorage(Page page, String kind, String key, String value) {
        String store = "session".equals(kind) ? "sessionStorage" : "localStorage";
        page.evaluate("([s, k, v]) => window[s].setItem(k, v)", List.of(store, key, value));
    }

    /**
     * Clears all entries from the specified web storage.
     *
     * @param page the Playwright page
     * @param kind "session" for sessionStorage, anything else for localStorage
     */
    public void clearStorage(Page page, String kind) {
        String store = "session".equals(kind) ? "sessionStorage" : "localStorage";
        page.evaluate("s => window[s].clear()", store);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Number asNumber(Object value) {
        return value instanceof Number n ? n : null;
    }

    private static Boolean asBoolean(Object value) {
        return value instanceof Boolean b ? b : null;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
