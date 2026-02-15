package com.renemrhfr.javawebagent.core;

/**
 * Describes an open browser tab with its identifier, title, and URL.
 *
 * @param targetId unique tab identifier
 * @param title    the page title
 * @param url      the current page URL
 *

 */
public record BrowserTab(String targetId, String title, String url) {}
