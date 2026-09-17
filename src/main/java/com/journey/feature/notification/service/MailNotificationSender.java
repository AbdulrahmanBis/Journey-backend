package com.journey.feature.notification.service;

import com.journey.feature.notification.spi.RecipientDirectory;
import com.journey.feature.notification.template.NotificationTemplate;
import com.journey.feature.notification.template.TemplateRenderer;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sends the {@code mail.xml} half of a template.
 *
 * <p>Each recipient is resolved, rendered and sent <em>individually</em> rather than as one message
 * with several addresses. That is what makes per-recipient language possible, and it keeps
 * recipients from seeing each other's addresses.
 *
 * <p>Sending is best-effort per recipient: one bad address does not stop the rest, and no failure
 * here can affect the in-app notification, which is written separately.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailNotificationSender {

    private final RecipientDirectory recipients;
    private final TemplateRenderer renderer;
    private final Optional<JavaMailSender> mailSender;
    private final com.journey.feature.notification.template.TemplateRegistry templates;

    static final String TEST_TEMPLATE = "mail-test";

    @Value("${app.frontend-url:http://localhost:4200}")
    private String appUrl;

    /**
     * Off by default. Without this, a developer running the app locally would start sending real
     * email to real colleagues the first time someone assigned a journey.
     */
    @Value("${app.notifications.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.notifications.mail.from:journey@localhost}")
    private String from;

    @Value("${app.notifications.default-language:en}")
    private String defaultLanguage;

    public void send(NotificationTemplate template, Set<String> recipientIds,
                     Map<String, String> variables) {
        if (!enabled) {
            logWouldSend(template, recipientIds, variables);
            return;
        }
        if (mailSender.isEmpty()) {
            log.warn("app.notifications.mail.enabled=true but no mail sender is configured "
                    + "(set app.mail.host / SMTP_HOST). Template '{}' was not emailed.", template.id());
            return;
        }

        for (String userId : recipientIds) {
            recipients.find(userId).ifPresentOrElse(
                    recipient -> sendTo(recipient, template, variables),
                    () -> log.warn("No recipient found for user id {} — '{}' not emailed.",
                            userId, template.id()));
        }
    }

    private void sendTo(RecipientDirectory.Recipient recipient, NotificationTemplate template,
                        Map<String, String> variables) {
        if (recipient.email() == null || recipient.email().isBlank()) {
            log.warn("User {} has no email address — '{}' not emailed.",
                    recipient.userId(), template.id());
            return;
        }

        String language = NotificationService.resolveLanguage(
                recipient.language() != null ? recipient.language() : defaultLanguage);
        NotificationTemplate.MailContent content = template.mailFor(language);
        if (content == null) {
            log.warn("Template '{}' lists MAIL but has no mail.xml.", template.id());
            return;
        }

        try {
            deliver(recipient.email(), content, variables);
            log.debug("Emailed '{}' to {} in {}.", template.id(), recipient.email(), language);
        // MailException is itself a RuntimeException, so RuntimeException covers it.
        } catch (jakarta.mail.MessagingException | RuntimeException e) {
            log.error("Could not email '{}' to {}: {}",
                    template.id(), recipient.email(), e.getMessage());
        }
    }

    private void deliver(String to, NotificationTemplate.MailContent content, Map<String, String> variables)
            throws jakarta.mail.MessagingException {
        String subject = renderer.render(content.subject(), variables);
        // The body is HTML: values are text (titles, names, what someone typed), so they are escaped.
        String body = renderer.render(content.body(), htmlEscaped(variables));

        MimeMessage message = mailSender.get().createMimeMessage();
        MimeMessageHelper helper =
                new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true);
        mailSender.get().send(message);
    }

    /**
     * Sends the {@code mail-test} template to one person right now, and reports failure instead of logging it —
     * so an Admin can check the SMTP settings without waiting for a real notification.
     *
     * @return the address it went to
     * @throws IllegalStateException with a message fit to show, when email is off, not configured, or rejected
     */
    public String sendTest(String userId) {
        if (!enabled) {
            throw new IllegalStateException("Email is turned off. Set MAIL_ENABLED=true (app.notifications.mail.enabled) and restart.");
        }
        if (mailSender.isEmpty()) {
            throw new IllegalStateException("No mail server is configured. Set SMTP_HOST (app.mail.host) and restart.");
        }
        RecipientDirectory.Recipient recipient = recipients.find(userId)
                .orElseThrow(() -> new IllegalStateException("Your account could not be found."));
        if (recipient.email() == null || recipient.email().isBlank()) {
            throw new IllegalStateException("Your account has no email address.");
        }
        NotificationTemplate template = templates.get(TEST_TEMPLATE);
        String language = NotificationService.resolveLanguage(
                recipient.language() != null ? recipient.language() : defaultLanguage);
        Map<String, String> variables = Map.of(
                "recipientName", recipient.name() == null ? "" : recipient.name(),
                "link", template.config().link(),
                "appUrl", appUrl);
        try {
            deliver(recipient.email(), template.mailFor(language), variables);
        } catch (jakarta.mail.MessagingException | RuntimeException e) {
            log.error("Test email to {} failed: {}", recipient.email(), e.getMessage());
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            throw new IllegalStateException("The mail server did not accept the message: " + cause.getMessage());
        }
        log.info("Test email sent to {}.", recipient.email());
        return recipient.email();
    }

    /** For the Admin mail check: whether sending is switched on, a server is set, and the sender address. */
    public Map<String, Object> status() {
        return Map.of("enabled", enabled, "configured", mailSender.isPresent(), "from", from);
    }

    /** One line at startup, so a half-configured server is noticed before anyone misses an email. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void reportConfiguration() {
        if (!enabled) {
            log.info("Notification email is off (app.notifications.mail.enabled=false); messages are logged, not sent.");
        } else if (mailSender.isEmpty()) {
            log.warn("Notification email is ON but no mail server is configured — set app.mail.host (SMTP_HOST). Nothing will be sent.");
        } else {
            log.info("Notification email is on, sending from {}.", from);
        }
    }

    private static Map<String, String> htmlEscaped(Map<String, String> variables) {
        Map<String, String> escaped = new java.util.HashMap<>();
        variables.forEach((name, value) -> escaped.put(name, org.springframework.web.util.HtmlUtils.htmlEscape(value)));
        return escaped;
    }

    /**
     * Renders anyway when sending is off, so a broken subject or body shows up in the log now
     * rather than on the day sending is switched on.
     */
    private void logWouldSend(NotificationTemplate template, Set<String> recipientIds,
                              Map<String, String> variables) {
        NotificationTemplate.MailContent content =
                template.mailFor(NotificationService.resolveLanguage(defaultLanguage));
        if (content == null) {
            log.warn("Template '{}' lists MAIL but has no mail.xml.", template.id());
            return;
        }
        try {
            log.info("Mail disabled (app.notifications.mail.enabled=false) — would send \"{}\" "
                            + "to {} recipient(s) for template '{}'.",
                    renderer.render(content.subject(), variables), recipientIds.size(), template.id());
        } catch (RuntimeException e) {
            log.error("Template '{}' has an unrenderable mail subject: {}", template.id(), e.getMessage());
        }
    }
}
