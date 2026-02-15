package com.renemrhfr.javawebagent.snapshot;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.renemrhfr.javawebagent.core.RoleRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a structured snapshot of a page's ARIA accessibility tree.
 *
 * <p>Parses Playwright's {@code ariaSnapshot()} output, filters elements
 * by visibility, assigns addressable refs to interactive elements, and
 * optionally annotates them with state details (value, checked, expanded,
 * available choices, etc.).</p>
 *
 */
public final class RoleSnapshotBuilder {
  private static final Set<String> INTERACTIVE_ROLES = Set.of(
      "button",
      "link",
      "textbox",
      "checkbox",
      "radio",
      "combobox",
      "listbox",
      "menuitem",
      "menuitemcheckbox",
      "menuitemradio",
      "option",
      "searchbox",
      "slider",
      "spinbutton",
      "switch",
      "tab",
      "treeitem"
  );

  private static final Set<String> CONTENT_ROLES = Set.of(
      "heading",
      "cell",
      "gridcell",
      "columnheader",
      "rowheader",
      "listitem",
      "article",
      "region",
      "main",
      "navigation"
  );

  private static final Pattern ROLE_LINE =
      Pattern.compile("^(\\s*)-\\s*([\\w-]+)(?:\\s+\"([^\"]*)\")?(.*)$");

  private static final Pattern TEXT_LINE =
      Pattern.compile("^(\\s*)-\\s*text:\\s*(.+)$");

  private static final int DEFAULT_MAX_CHARS = 10_000;
  private static final String TRUNCATED_MARKER = "\n[...TRUNCATED - page too large]";
  private static final int MAX_TEXT_LINE_CHARS = 220;
  private static final int MAX_VALUE_CHARS = 120;
  private static final int MAX_OPTION_CHARS = 60;

  /**
   * Takes a snapshot with default settings.
   *
   * @param page the Playwright page
   * @return the snapshot result
   */
  public SnapshotResult snapshot(Page page) {
    return snapshot(page, DEFAULT_MAX_CHARS);
  }

  /**
   * Takes a snapshot with a custom character limit.
   *
   * @param page     the Playwright page
   * @param maxChars maximum output length in characters
   * @return the snapshot result
   */
  public SnapshotResult snapshot(Page page, int maxChars) {
    return snapshot(page, maxChars, true, true);
  }

  /**
   * Takes a snapshot with full control over filtering and detail level.
   *
   * @param page              the Playwright page
   * @param maxChars          maximum output length in characters
   * @param filterVisibleOnly only include visible interactive elements
   * @param includeDetails    annotate elements with state details
   * @return the snapshot result containing text and addressable refs
   */
  public SnapshotResult snapshot(Page page, int maxChars, boolean filterVisibleOnly, boolean includeDetails) {
    String ariaSnapshot;
    try {
      ariaSnapshot = page.locator("body").ariaSnapshot();
    } catch (RuntimeException ignored) {
      ariaSnapshot = "";
    }

    String[] lines = ariaSnapshot.split("\\R");

    // First pass: count interactive elements per role+name for nth tracking
    Map<String, Integer> countsByKey = new LinkedHashMap<>();
    List<ParsedLine> parsed = new ArrayList<>();

    for (String line : lines) {
      // Check for text content lines (e.g. "- text: Some paragraph")
      Matcher textMatcher = TEXT_LINE.matcher(line);
      if (textMatcher.matches()) {
        String text = truncate(normalizeInline(textMatcher.group(2)), MAX_TEXT_LINE_CHARS);
        if (!text.isBlank()) {
          parsed.add(new ParsedLine(null, null, text, null, -1, true, indentDepth(textMatcher.group(1))));
        }
        continue;
      }

      Matcher matcher = ROLE_LINE.matcher(line);
      if (!matcher.matches()) {
        continue;
      }

      String role = matcher.group(2).toLowerCase(Locale.ROOT);
      String name = normalizeName(matcher.group(3));

      if (INTERACTIVE_ROLES.contains(role)) {
        String key = role + "|" + name;
        int nth = countsByKey.getOrDefault(key, 0);
        countsByKey.put(key, nth + 1);
        parsed.add(new ParsedLine(role, name, null, key, nth, false, indentDepth(matcher.group(1))));
      } else if (CONTENT_ROLES.contains(role) && !name.isBlank()) {
        parsed.add(new ParsedLine(role, name, null, null, -1, true, indentDepth(matcher.group(1))));
      }
    }

    // Second pass: filter interactive elements by visibility, keep content as-is
    Map<String, RoleRef> refs = new LinkedHashMap<>();
    List<String> outputLines = new ArrayList<>();
    Map<String, Integer> visibleCountsByKey = new LinkedHashMap<>();
    Map<String, Integer> totalsByKey = new LinkedHashMap<>();
    int counter = 0;

    for (ParsedLine pl : parsed) {
      if (pl.contextOnly) {
        // Content role or text line — include as context without a ref
        String indent = "  ".repeat(Math.max(0, pl.depth));
        if (pl.role != null) {
          outputLines.add(indent + "- " + pl.role + " '" + pl.name + "'");
        } else {
          outputLines.add(indent + "- text: " + pl.text);
        }
        continue;
      }

      // Interactive element — check visibility
      if (filterVisibleOnly && !isVisible(page, pl.role, pl.name, pl.nth)) {
        continue;
      }

      int visibleNth = visibleCountsByKey.getOrDefault(pl.key, 0);
      visibleCountsByKey.put(pl.key, visibleNth + 1);
      totalsByKey.merge(pl.key, 1, Integer::sum);

      counter += 1;
      String ref = "e" + counter;
      refs.put(ref, new RoleRef(ref, "", pl.role, pl.name, visibleNth));

      String displayName = pl.name.isBlank() ? "(unnamed)" : pl.name;
      String indent = "  ".repeat(Math.max(0, pl.depth));
      String line = indent + "- " + pl.role + " '" + displayName + "' [ref=" + ref + "]";
      if (includeDetails) {
        line += describeInteractive(page, pl.role, pl.name, visibleNth);
      }
      outputLines.add(line);
    }

    // Fix nth: remove nth for non-duplicates
    for (Map.Entry<String, RoleRef> entry : refs.entrySet()) {
      RoleRef r = entry.getValue();
      if (totalsByKey.getOrDefault(r.role() + "|" + r.name(), 0) <= 1) {
        refs.put(entry.getKey(), new RoleRef(r.ref(), r.selector(), r.role(), r.name(), 0));
      }
    }

    if (outputLines.isEmpty()) {
      return new SnapshotResult("(no interactive elements)", refs);
    }

    // Build output with maxChars cap — always include interactive elements, truncate context
    StringBuilder sb = new StringBuilder();
    for (String line : outputLines) {
      if (sb.length() + line.length() + 1 > maxChars) {
        sb.append(TRUNCATED_MARKER);
        break;
      }
      if (!sb.isEmpty()) {
        sb.append('\n');
      }
      sb.append(line);
    }

    return new SnapshotResult(sb.toString(), refs);
  }

  private static String normalizeName(String raw) {
    return normalizeInline(raw);
  }

  private boolean isVisible(Page page, String role, String name, int nth) {
    AriaRole ariaRole;
    try {
      ariaRole = AriaRole.valueOf(role.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return true; // if we can't parse the role, include it anyway
    }

    Locator locator;
    if (name != null && !name.isBlank()) {
      locator = page.getByRole(ariaRole, new Page.GetByRoleOptions().setName(name));
    } else {
      locator = page.getByRole(ariaRole);
    }

    try {
      Locator candidate = locator.nth(nth);
      return candidate.isVisible();
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private String optionChoices(Page page, String role, String name, int nth) {
    AriaRole ariaRole;
    try {
      ariaRole = AriaRole.valueOf(role.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return "";
    }

    Locator locator;
    if (name != null && !name.isBlank()) {
      locator = page.getByRole(ariaRole, new Page.GetByRoleOptions().setName(name));
    } else {
      locator = page.getByRole(ariaRole);
    }

    Locator candidate;
    try {
      int count = locator.count();
      if (count <= 0) {
        return "";
      }
      candidate = locator.nth(Math.min(Math.max(nth, 0), count - 1));
    } catch (RuntimeException ignored) {
      return "";
    }

    Object raw;
    try {
      raw = candidate.evaluate("""
          el => {
            const out = [];
            const seen = new Set();
            const add = (value) => {
              const v = String(value ?? "").replace(/\\s+/g, " ").trim();
              if (!v || seen.has(v)) {
                return;
              }
              seen.add(v);
              out.push(v);
            };

            if (el instanceof HTMLSelectElement) {
              for (const opt of Array.from(el.options)) {
                add(opt.label || opt.textContent || opt.value);
              }
              return out.slice(0, 10);
            }

            if (el.getAttribute("role") === "listbox") {
              for (const opt of Array.from(el.querySelectorAll('[role="option"], option'))) {
                add(opt.getAttribute("aria-label") || opt.textContent || opt.getAttribute("label") || opt.getAttribute("value"));
              }
              return out.slice(0, 10);
            }

            const controls = (el.getAttribute("aria-controls") || "").trim();
            if (controls) {
              const ids = controls.split(/\\s+/);
              for (const id of ids) {
                const root = document.getElementById(id);
                if (!root) continue;
                for (const opt of Array.from(root.querySelectorAll('[role="option"], option'))) {
                  add(opt.getAttribute("aria-label") || opt.textContent || opt.getAttribute("label") || opt.getAttribute("value"));
                }
              }
            }
            return out.slice(0, 10);
          }
          """);
    } catch (RuntimeException ignored) {
      return "";
    }

    List<String> values = toStringList(raw);
    if (values.isEmpty()) {
      return "";
    }
    return values.stream()
        .map(v -> truncate(v, MAX_OPTION_CHARS))
        .collect(java.util.stream.Collectors.joining(" | "));
  }

  private String describeInteractive(Page page, String role, String name, int nthVisible) {
    Locator candidate = visibleCandidate(page, role, name, nthVisible);
    if (candidate == null) {
      return "";
    }

    Object raw;
    try {
      raw = candidate.evaluate("""
          el => {
            const out = {};
            const normalize = (v) => String(v ?? "").replace(/\\s+/g, " ").trim();
            const set = (k, v) => {
              if (v === null || v === undefined) return;
              if (typeof v === "string" && !v.trim()) return;
              out[k] = v;
            };

            const aria = (name) => el.getAttribute(name);
            const role = normalize(aria("role"));

            const disabled = aria("aria-disabled") === "true" || (typeof el.disabled === "boolean" && el.disabled);
            const readonly = aria("aria-readonly") === "true" || (typeof el.readOnly === "boolean" && el.readOnly);
            const required = aria("aria-required") === "true" || (typeof el.required === "boolean" && el.required);
            if (disabled) set("disabled", true);
            if (readonly) set("readonly", true);
            if (required) set("required", true);

            const expanded = aria("aria-expanded");
            if (expanded === "true" || expanded === "false") {
              set("expanded", expanded);
            }

            const checkedAttr = aria("aria-checked");
            if (checkedAttr === "true" || checkedAttr === "false" || checkedAttr === "mixed") {
              set("checked", checkedAttr);
            } else if (typeof el.checked === "boolean") {
              set("checked", el.checked ? "true" : "false");
            }

            const pressed = aria("aria-pressed");
            if (pressed === "true" || pressed === "false" || pressed === "mixed") {
              set("pressed", pressed);
            }

            if (
              el instanceof HTMLInputElement ||
              el instanceof HTMLTextAreaElement ||
              el instanceof HTMLSelectElement
            ) {
              if (!(el instanceof HTMLInputElement && el.type === "password")) {
                const value = normalize(el.value);
                if (value) set("value", value);
              }
            } else {
              const current = normalize(aria("aria-valuetext") || aria("aria-valuenow"));
              if (current) set("value", current);
            }

            if (el instanceof HTMLSelectElement) {
              const selected = [];
              for (const opt of Array.from(el.selectedOptions)) {
                const text = normalize(opt.label || opt.textContent || opt.value);
                if (text) selected.push(text);
                if (selected.length >= 5) break;
              }
              if (selected.length) set("selected", selected);
            }

            const choices = [];
            const seen = new Set();
            const addChoice = (v) => {
              const normalized = normalize(v);
              if (!normalized || seen.has(normalized)) return;
              seen.add(normalized);
              choices.push(normalized);
            };

            if (el instanceof HTMLSelectElement) {
              for (const opt of Array.from(el.options)) {
                addChoice(opt.label || opt.textContent || opt.value);
                if (choices.length >= 10) break;
              }
            } else if (role === "listbox") {
              for (const opt of Array.from(el.querySelectorAll('[role="option"], option'))) {
                addChoice(opt.getAttribute("aria-label") || opt.textContent || opt.getAttribute("label") || opt.getAttribute("value"));
                if (choices.length >= 10) break;
              }
            } else {
              const controls = normalize(aria("aria-controls"));
              if (controls) {
                const ids = controls.split(/\\s+/);
                for (const id of ids) {
                  const root = document.getElementById(id);
                  if (!root) continue;
                  for (const opt of Array.from(root.querySelectorAll('[role="option"], option'))) {
                    addChoice(opt.getAttribute("aria-label") || opt.textContent || opt.getAttribute("label") || opt.getAttribute("value"));
                    if (choices.length >= 10) break;
                  }
                  if (choices.length >= 10) break;
                }
              }
            }
            if (choices.length) set("choices", choices);

            return out;
          }
          """);
    } catch (RuntimeException ignored) {
      return "";
    }

    if (!(raw instanceof Map<?, ?> map)) {
      return "";
    }

    List<String> tags = new ArrayList<>();
    addStringTag(tags, "value", map.get("value"), MAX_VALUE_CHARS);
    addStringTag(tags, "expanded", map.get("expanded"), 16);
    addStringTag(tags, "checked", map.get("checked"), 16);
    addStringTag(tags, "pressed", map.get("pressed"), 16);
    addBooleanTag(tags, "disabled", map.get("disabled"));
    addBooleanTag(tags, "readonly", map.get("readonly"));
    addBooleanTag(tags, "required", map.get("required"));

    String selected = listTagValue(map.get("selected"), 5, MAX_OPTION_CHARS);
    if (!selected.isBlank()) {
      tags.add("selected=" + selected);
    }
    String choices = listTagValue(map.get("choices"), 10, MAX_OPTION_CHARS);
    if (!choices.isBlank()) {
      tags.add("choices=" + choices);
    } else if ("combobox".equals(role) || "listbox".equals(role)) {
      // Explicit fallback signal for planners.
      String fallback = optionChoices(page, role, name, nthVisible);
      if (!fallback.isBlank()) {
        tags.add("choices=" + fallback);
      }
    }

    if (tags.isEmpty()) {
      return "";
    }
    return " [" + String.join("] [", tags) + "]";
  }

  private static void addStringTag(List<String> tags, String key, Object value, int maxChars) {
    if (value == null) {
      return;
    }
    String normalized = normalizeInline(value.toString());
    if (normalized.isBlank()) {
      return;
    }
    tags.add(key + "=" + truncate(normalized, maxChars));
  }

  private static void addBooleanTag(List<String> tags, String key, Object value) {
    if (!(value instanceof Boolean b) || !b) {
      return;
    }
    tags.add(key + "=true");
  }

  private static String listTagValue(Object raw, int maxItems, int maxItemChars) {
    List<String> values = toStringList(raw);
    if (values.isEmpty()) {
      return "";
    }
    return values.stream()
        .limit(Math.max(1, maxItems))
        .map(v -> truncate(v, maxItemChars))
        .collect(java.util.stream.Collectors.joining(" | "));
  }

  private Locator visibleCandidate(Page page, String role, String name, int nthVisible) {
    AriaRole ariaRole;
    try {
      ariaRole = AriaRole.valueOf(role.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return null;
    }

    Locator locator;
    if (name != null && !name.isBlank()) {
      locator = page.getByRole(ariaRole, new Page.GetByRoleOptions().setName(name));
    } else {
      locator = page.getByRole(ariaRole);
    }

    int found = 0;
    int count = locator.count();
    for (int i = 0; i < count; i++) {
      Locator candidate = locator.nth(i);
      try {
        if (!candidate.isVisible()) {
          continue;
        }
      } catch (RuntimeException ignored) {
        continue;
      }
      if (found == Math.max(0, nthVisible)) {
        return candidate;
      }
      found += 1;
    }
    return null;
  }

  private static String normalizeInline(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().replaceAll("\\s+", " ");
  }

  private static String truncate(String value, int maxChars) {
    if (value == null) {
      return "";
    }
    if (value.length() <= maxChars) {
      return value;
    }
    return value.substring(0, Math.max(0, maxChars - 3)) + "...";
  }

  private static int indentDepth(String indent) {
    if (indent == null || indent.isEmpty()) {
      return 0;
    }
    return Math.max(0, indent.length() / 2);
  }

  private static List<String> toStringList(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (Object value : list) {
      if (value == null) {
        continue;
      }
      String normalized = value.toString().trim().replaceAll("\\s+", " ");
      if (!normalized.isBlank() && !out.contains(normalized)) {
        out.add(normalized);
      }
      if (out.size() >= 10) {
        break;
      }
    }
    return out;
  }

  private record ParsedLine(String role, String name, String text, String key, int nth, boolean contextOnly, int depth) {}
}
