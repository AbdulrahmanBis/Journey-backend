package com.journey.feature.notification.template;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes {@code ${name}} placeholders in template text.
 *
 * <p>Deliberately not a general expression language. Templates are content, edited by whoever owns
 * the wording, and a template engine that can call methods on the objects handed to it is a code
 * execution surface. This does exactly one thing: replace a name with a string.
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z0-9_.-]+)}");

    /**
     * Replaces every placeholder with its value.
     *
     * @throws IllegalArgumentException if the text uses a name that was not supplied, so a missing
     *                                  value surfaces as a failure rather than as literal
     *                                  {@code ${...}} in someone's inbox
     */
    public String render(String text, Map<String, String> variables) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables.get(name);
            if (value == null) {
                throw new IllegalArgumentException("No value supplied for ${" + name + "}");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Every placeholder name used in the given texts, in first-seen order. */
    public Set<String> placeholdersIn(String... texts) {
        Set<String> names = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) continue;
            Matcher matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }
}
