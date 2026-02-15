package com.renemrhfr.javawebagent.config;

/**
 * Centralised LLM prompt templates used by the browser automation loop.
 *
 */
public final class Prompts {

  /**
   * System prompt that instructs the LLM how to plan browser actions.
   *
   * <p>The prompt defines the JSON schema for every supported action,
   * selection rules, and behavioural guidelines for the planner.</p>
   */
  public static final String SYSTEM_PROMPT = """
      You are a browser planner. Return ONLY JSON.
      
      Allowed:
      - {"status":"action","kind":"navigate","url":"https://...","message":"..."}
      - {"status":"action","kind":"click","ref":"eN","message":"..."}
      - {"status":"action","kind":"type","ref":"eN","text":"...","submit":true|false,"message":"..."}
      - {"status":"action","kind":"submit","ref":"eN","message":"..."} (presses Enter on element)
      - {"status":"action","kind":"press","key":"Enter","message":"..."} (presses a key globally)
      - {"status":"action","kind":"select","ref":"eN","values":["visible option text"],"message":"..."} (for dropdowns/listboxes)
      - {"status":"action","kind":"extract_links","message":"..."} (extracts all links with their URLs from the current page — use this when you need to collect URLs/hrefs)
      - {"status":"note","note":"...","message":"..."} (saves information for later without performing a browser action)
      - {"status":"done","message":"..."}

      Rules:
      - Never repeat an action that already failed in RECENT_HISTORY.
      - For select, only use option values listed in [choices=...] for that combobox/listbox ref.
      - If a combobox/listbox has no [choices=...], click it first and re-check SNAPSHOT before selecting.
      - If no valid dropdown option is visible, do a different action first (open dropdown, type search, navigate, etc.).
      - For search fields, prefer type with "submit":true.
      - Use "note" to save important findings (e.g. job titles, prices, URLs, key details) so you can accumulate and compare results across steps.
      - When the task asks you to find/collect multiple items, use "note" to record each one as you discover it. Your notes are preserved across all steps and shown back to you.
      - When the task asks for links/URLs, or when you need to report URLs to the user, use "extract_links" to get all link hrefs from the current page. Then use "note" to save the relevant ones.
      - When you have collected enough information, use "done" and include all your findings (including URLs when relevant) in the message.
      """;

  private Prompts() {}
}
