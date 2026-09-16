package com.journey.feature.notification.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A template's {@code config.json} — who is notified, on which channels, and with what.
 *
 * <pre>
 * {
 *   "channels":   ["IN_APP"],
 *   "recipients": ["learner"],
 *   "link":       "/journey-log/${learnerJourneyId}",
 *   "variables":  ["assignerName", "journeyTitle", "learnerJourneyId"]
 * }
 * </pre>
 *
 * <p>{@code recipients} are party names, matched against the {@code parties} map on the incoming
 * request — so who gets notified is a file edit, not a code change.
 *
 * <p>{@code variables} is the declared list. It is enforced in both directions at load time: every
 * {@code ${...}} used in the content must be declared, and at dispatch every declared variable must
 * be supplied. That turns a typo into a startup-time error instead of an email reading
 * "assigned you to ${journeyTitle}".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TemplateConfig(
        List<String> channels,
        List<String> recipients,
        String link,
        List<String> variables
) {
    public TemplateConfig {
        channels = channels == null ? List.of() : List.copyOf(channels);
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
        variables = variables == null ? List.of() : List.copyOf(variables);
    }
}
