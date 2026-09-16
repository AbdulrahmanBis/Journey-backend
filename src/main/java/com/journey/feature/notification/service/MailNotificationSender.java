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
                    + "(set spring.mail.host). Template '{}' was not emailed.", template.id());
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
            String subject = renderer.render(content.subject(), variables);
            String body = renderer.render(content.body(), variables);

            MimeMessage message = mailSender.get().createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient.email());
            helper.setSubject(subject);
            // The template body is HTML.
            helper.setText(body, true);

            mailSender.get().send(message);
            log.debug("Emailed '{}' to {} in {}.", template.id(), recipient.email(), language);
        // MailException is itself a RuntimeException, so RuntimeException covers it.
        } catch (jakarta.mail.MessagingException | RuntimeException e) {
            log.error("Could not email '{}' to {}: {}",
                    template.id(), recipient.email(), e.getMessage());
        }
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
