package com.journey.feature.notification.api;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything the notification module needs in order to notify someone, and nothing else.
 *
 * <p>This is the contract across the seam. A caller names a template, names the people involved,
 * and supplies the values the template's text refers to. It says nothing about channels or about
 * who actually gets notified — that is the template's {@code config.json} to decide, so changing
 * "also email the manager" is a file edit rather than a code change.
 *
 * <p><b>parties</b> maps a symbolic name to a user id: {@code {"learner": "u-ln-1",
 * "assigner": "u-mgr"}}. The template config picks which of those names are recipients. This is
 * what keeps the notification module free of any journey concept — it never learns what a learner
 * is, only that something called "learner" resolved to a user id.
 *
 * <p><b>variables</b> are substituted into {@code ${...}} placeholders in the title, body, mail and
 * link. The template declares which ones it expects, and dispatch fails loudly if one is missing
 * rather than sending "assigned you to ${journeyTitle}".
 */
public record NotificationRequest(
        String templateId,
        Map<String, String> parties,
        Map<String, String> variables
) {

    public static Builder forTemplate(String templateId) {
        return new Builder(templateId);
    }

    public static final class Builder {
        private final String templateId;
        private final Map<String, String> parties = new HashMap<>();
        private final Map<String, String> variables = new HashMap<>();

        private Builder(String templateId) {
            this.templateId = templateId;
        }

        /** Names someone involved, so the template config can choose to notify them. */
        public Builder party(String name, String userId) {
            if (userId != null) {
                parties.put(name, userId);
            }
            return this;
        }

        /** Supplies a {@code ${name}} value. Nulls become empty strings rather than "null". */
        public Builder variable(String name, Object value) {
            variables.put(name, value == null ? "" : String.valueOf(value));
            return this;
        }

        public NotificationRequest build() {
            return new NotificationRequest(templateId, Map.copyOf(parties), Map.copyOf(variables));
        }
    }
}
