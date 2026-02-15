package com.renemrhfr.javawebagent.snapshot;

import com.renemrhfr.javawebagent.core.RoleRef;
import java.util.Map;

/**
 * Result of an accessibility snapshot, containing the textual
 * representation and a map of addressable element refs.
 *
 * @param text the formatted snapshot text
 * @param refs map from ref ID (e.g. "e1") to the corresponding {@link RoleRef}
 *

 */
public record SnapshotResult(String text, Map<String, RoleRef> refs) {}
