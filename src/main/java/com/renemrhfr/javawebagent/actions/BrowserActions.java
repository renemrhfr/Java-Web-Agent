package com.renemrhfr.javawebagent.actions;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import com.renemrhfr.javawebagent.core.RoleRef;
import com.renemrhfr.javawebagent.security.NavigationSecurity;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Browser actions executed against Playwright locators.
 *
 * <p>Each method resolves an element via its {@link RoleRef} (ARIA role +
 * name) or CSS selector, then performs the requested interaction. All
 * navigation targets are validated through {@link NavigationSecurity}.</p>
 *
 */
public final class BrowserActions {

    /**
     * Navigates the page to the given URL
     *
     * @param page      the Playwright page
     * @param url       the target URL
     * @param timeoutMs navigation timeout in milliseconds
     */
    public void navigate(Page page, String url, int timeoutMs) {
        NavigationSecurity.assertAllowed(url);
        page.navigate(url, new Page.NavigateOptions().setTimeout(timeoutMs));
        NavigationSecurity.assertAllowed(page.url());
    }

    /**
     * Clicks the element
     *
     * @param page      the Playwright page
     * @param ref       the target element reference
     * @param timeoutMs click timeout in milliseconds
     */
    public void click(Page page, RoleRef ref, int timeoutMs) {
        locator(page, ref).click(new Locator.ClickOptions().setTimeout(timeoutMs).setNoWaitAfter(true));
    }

    /**
     * Hovers over the given element
     *
     * @param page      the Playwright page
     * @param ref       the target element reference
     * @param timeoutMs hover timeout in milliseconds
     */
    public void hover(Page page, RoleRef ref, int timeoutMs) {
        locator(page, ref).hover(new Locator.HoverOptions().setTimeout(timeoutMs));
    }

    /**
     * Fills the element with the given value (replaces existing content).
     *
     * @param page      the Playwright page
     * @param ref       the target element reference
     * @param value     the value to fill
     * @param timeoutMs fill timeout in milliseconds
     */
    public void fill(Page page, RoleRef ref, String value, int timeoutMs) {
        locator(page, ref).fill(value, new Locator.FillOptions().setTimeout(timeoutMs));
    }

    /**
     * Types text into the element character by character.
     *
     * @param page      the Playwright page
     * @param ref       the target element reference
     * @param text      the text to type
     * @param timeoutMs type timeout in milliseconds
     */
    public void type(Page page, RoleRef ref, String text, int timeoutMs) {
        type(page, ref, text, timeoutMs, false);
    }

    /**
     * Types text into the element, optionally submitting the form afterwards.
     *
     * @param page      the Playwright page
     * @param ref       the target element reference
     * @param text      the text to type
     * @param timeoutMs type timeout in milliseconds
     * @param submit    whether to submit the form after typing
     */
    public void type(Page page, RoleRef ref, String text, int timeoutMs, boolean submit) {
        Locator target = locator(page, ref);
        target.fill("", new Locator.FillOptions().setTimeout(timeoutMs));
        target.type(text, new Locator.TypeOptions().setTimeout(timeoutMs));
        if (submit) {
            submit(page, ref, timeoutMs);
        }
    }

    /**
     * Selects one or more options in a dropdown, listbox, or combobox.
     *
     * @param page      the Playwright page
     * @param ref       the target element reference
     * @param values    the option texts to select
     * @param timeoutMs selection timeout in milliseconds
     */
    public void select(Page page, RoleRef ref, List<String> values, int timeoutMs) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values are required");
        }

        List<String> normalizedValues = values.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).toList();
        if (normalizedValues.isEmpty()) {
            throw new IllegalArgumentException("values are required");
        }

        Locator target = locator(page, ref);
        String tagName = elementTagName(target);
        try {
            if ("select".equals(tagName)) {
                target.selectOption(normalizedValues.toArray(String[]::new), new Locator.SelectOptionOptions().setTimeout(timeoutMs));
                return;
            }
        } catch (RuntimeException exception) {
            // Fallback to additional strategies below.
        }

        SelectOption[] byLabel = normalizedValues.stream().map(v -> new SelectOption().setLabel(v)).toArray(SelectOption[]::new);
        try {
            if ("select".equals(tagName)) {
                target.selectOption(byLabel, new Locator.SelectOptionOptions().setTimeout(timeoutMs));
                return;
            }
        } catch (RuntimeException ignored) {
            // Fallback to ARIA-based combobox/listbox selection below
        }

        target.click(new Locator.ClickOptions().setTimeout(timeoutMs));
        for (String value : normalizedValues) {
            Locator optionByName = findIfVisible(page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(value)), 0);
            if (optionByName == null) {
                optionByName = findIfVisible(page.getByText(value, new Page.GetByTextOptions().setExact(true)), 0);
            }
            if (optionByName == null) {
                throw new IllegalStateException("option not found for value: " + value);
            }
            try {
                optionByName.click(new Locator.ClickOptions().setTimeout(timeoutMs));
            } catch (RuntimeException e) {
                throw new IllegalStateException("failed selecting option: " + value, e);
            }
        }
    }

    /**
     * Drags one element onto another.
     *
     * @param page      the Playwright page
     * @param start     the element to drag
     * @param end       the drop target element
     * @param timeoutMs drag timeout in milliseconds
     */
    public void drag(Page page, RoleRef start, RoleRef end, int timeoutMs) {
        locator(page, start).dragTo(locator(page, end), new Locator.DragToOptions().setTimeout(timeoutMs));
    }

    /**
     * Presses a keyboard key globally on the page.
     *
     * @param page the Playwright page
     * @param key  the key to press (e.g. "Enter", "Tab")
     */
    public void press(Page page, String key) {
        page.keyboard().press(key);
    }

    /**
     * Submits the form associated with the given element, trying Enter key
     * first, then programmatic form submission, then a submit button fallback.
     *
     * @param page      the Playwright page
     * @param ref       the target element reference
     * @param timeoutMs submit timeout in milliseconds
     */
    public void submit(Page page, RoleRef ref, int timeoutMs) {
        Locator target = locator(page, ref);
        try {
            target.press("Enter", new Locator.PressOptions().setTimeout(timeoutMs));
            return;
        } catch (RuntimeException ignored) {
            // Fall through to form/button submit fallback.
        }

        try {
            Object submitted = target.evaluate("""
                    el => {
                      const htmlEl = el instanceof HTMLElement ? el : null;
                      if (!htmlEl) {
                        return false;
                      }
                      const form = htmlEl.closest("form");
                      if (form instanceof HTMLFormElement) {
                        if (typeof form.requestSubmit === "function") {
                          form.requestSubmit();
                        } else {
                          form.submit();
                        }
                        return true;
                      }
                      return false;
                    }
                    """);
            if (submitted instanceof Boolean b && b) {
                return;
            }
        } catch (RuntimeException ignored) {
            // Keep trying submit button fallback.
        }

        Locator submitButton = page.locator("button[type='submit'], input[type='submit'], [role='button'][type='submit']").first();
        submitButton.click(new Locator.ClickOptions().setTimeout(timeoutMs));
    }

    /**
     * Takes a screenshot of the page.
     *
     * @param page     the Playwright page
     * @param fullPage whether to capture the full scrollable page
     * @return PNG image bytes
     */
    public byte[] screenshot(Page page, boolean fullPage) {
        return page.screenshot(new Page.ScreenshotOptions().setFullPage(fullPage));
    }

    /**
     * Finds Locator. First by Role, as fallback by Selector
     */
    private Locator locator(Page page, RoleRef ref) {
        Locator byRole = roleLocator(page, ref);
        if (byRole != null) {
            return byRole;
        }

        if (ref.selector() != null && !ref.selector().isBlank()) {
            Locator bySelector = page.locator(ref.selector());
            Locator visible = findIfVisible(bySelector, Math.max(ref.nth(), 0));
            if (visible != null) {
                return visible;
            }
            int count = bySelector.count();
            if (count > 0) {
                return bySelector.nth(Math.min(Math.max(ref.nth(), 0), count - 1));
            }
            throw new IllegalArgumentException("no elements found for selector ref: " + ref.ref());
        }

        throw new IllegalArgumentException("cannot resolve ref: " + ref.ref());
    }

    private Locator roleLocator(Page page, RoleRef ref) {
        AriaRole role = parseAriaRole(ref.role());
        if (role == null) {
            return null;
        }

        Locator locator;
        if (ref.name() != null && !ref.name().isBlank()) {
            locator = page.getByRole(role, new Page.GetByRoleOptions().setName(ref.name()));
        } else {
            locator = page.getByRole(role);
        }
        Locator visible = findIfVisible(locator, Math.max(ref.nth(), 0));
        if (visible != null) {
            return visible;
        }
        int count = locator.count();
        if (count == 0) {
            throw new IllegalArgumentException("no elements found for role ref: role=" + ref.role() + " name='" + ref.name() + "'");
        }
        return locator.nth(Math.min(Math.max(ref.nth(), 0), count - 1));
    }

    private AriaRole parseAriaRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return AriaRole.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Locator findIfVisible(Locator candidates, int nthVisible) {
        int foundVisible = 0;
        int count = candidates.count();
        for (int i = 0; i < count; i++) {
            Locator current = candidates.nth(i);
            boolean visible;
            try {
                visible = current.isVisible();
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!visible) {
                continue;
            }
            if (foundVisible == nthVisible) {
                return current;
            }
            foundVisible += 1;
        }
        return null;
    }

    private String elementTagName(Locator locator) {
        try {
            Object tag = locator.evaluate("el => (el && el.tagName ? el.tagName.toLowerCase() : '')");
            return tag == null ? "" : String.valueOf(tag);
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
