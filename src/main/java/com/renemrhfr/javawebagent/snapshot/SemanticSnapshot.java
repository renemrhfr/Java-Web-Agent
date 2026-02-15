package com.renemrhfr.javawebagent.snapshot;

import com.microsoft.playwright.Page;
import com.renemrhfr.javawebagent.config.BrowserConfig;

/**
 * Captures semantic accessibility snapshots of a Playwright page.
 *
 * <p>Delegates to {@link RoleSnapshotBuilder} to produce a text
 * representation of the page's interactive and content elements,
 * along with addressable refs for each element.</p>
 *

 */
public final class SemanticSnapshot {
  private final RoleSnapshotBuilder roleSnapshotBuilder = new RoleSnapshotBuilder();

  public SnapshotResult capture(Page page) {
    return roleSnapshotBuilder.snapshot(page);
  }

  public SnapshotResult capture(Page page, BrowserConfig config) {
    return roleSnapshotBuilder.snapshot(
        page,
        config.snapshotMaxChars(),
        config.snapshotFilterVisibleOnly(),
        config.snapshotIncludeDetails()
    );
  }
}
