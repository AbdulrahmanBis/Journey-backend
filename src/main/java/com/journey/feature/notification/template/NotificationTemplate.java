package com.journey.feature.notification.template;

import java.util.Map;

/**
 * One template directory, loaded: its config plus its per-language content.
 *
 * <p>The maps are keyed by language tag ({@code "en"}, {@code "ar"}), matching the front end's
 * {@code assets/i18n} files. Both languages are required for the in-app content, the same parity
 * rule the UI translations follow, so a notification can never render in the wrong language just
 * because someone forgot a file.
 */
public record NotificationTemplate(
        String id,
        TemplateConfig config,
        Map<String, MessageContent> inApp,
        Map<String, MailContent> mail,
        long signature
) {

    /** The bell entry: what {@code push.json} holds. */
    public record MessageContent(String title, String body) {}

    /** The email: what {@code mail.xml} holds. */
    public record MailContent(String subject, String body) {}

    public MessageContent inAppFor(String language) {
        MessageContent content = inApp.get(language);
        return content != null ? content : inApp.get(TemplateRegistry.DEFAULT_LANGUAGE);
    }

    public MailContent mailFor(String language) {
        MailContent content = mail.get(language);
        return content != null ? content : mail.get(TemplateRegistry.DEFAULT_LANGUAGE);
    }
}
