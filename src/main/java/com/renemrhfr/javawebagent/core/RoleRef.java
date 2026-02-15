package com.renemrhfr.javawebagent.core;

/**
 * An addressable reference to a page element, identified by its ARIA
 * role, accessible name, and optional nth-match index.
 *
 * @param ref      short identifier (e.g. "e1") used in snapshots and actions
 * @param selector optional CSS selector fallback
 * @param role     the ARIA role (e.g. "button", "textbox")
 * @param name     the accessible name of the element
 * @param nth      zero-based index when multiple elements share the same role and name
 *

 */
public record RoleRef(String ref, String selector, String role, String name, int nth) {}
