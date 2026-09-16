package com.journey.feature.notification.service;

import tools.jackson.databind.ObjectMapper;
import com.journey.common.enums.NotificationChannel;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.notification.api.NotificationDispatcher;
import com.journey.feature.notification.api.NotificationRequest;
import com.journey.feature.notification.entity.Notification;
import com.journey.feature.notification.repository.NotificationRepository;
import com.journey.feature.notification.template.NotificationTemplate;
import com.journey.feature.notification.template.TemplateRegistry;
import com.journey.feature.notification.template.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-process implementation of the notification seam.
 *
 * <p>Runs on a background thread: a notification must never slow down or fail the action that
 * raised it. Everything is wrapped so that a broken template, a missing variable or a database
 * hiccup produces a log line, not a failed assignment — the caller has already committed its work
 * by the time this runs, and there is nothing useful it could do with an exception anyway.
 *
 * <p>When notifications move to their own service, this class is replaced by one that posts the
 * same {@link NotificationRequest} over HTTP. Callers do not change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalNotificationDispatcher implements NotificationDispatcher {

    private final TemplateRegistry templates;
    private final TemplateRenderer renderer;
    private final NotificationRepository repository;
    private final IdGeneratorService idGenerator;
    private final ObjectMapper mapper;
    private final MailNotificationSender mail;

    /** Site origin, so email templates can turn the in-app route into a clickable absolute URL. */
    @Value("${app.frontend-url:http://localhost:4200}")
    private String appUrl;

    @Override
    @Async
    public void dispatch(NotificationRequest request) {
        try {
            deliver(request);
        } catch (RuntimeException e) {
            log.error("Notification '{}' was not delivered: {}", request.templateId(), e.getMessage(), e);
        }
    }

    /*
     * No @Transactional here: it would be self-invocation from dispatch() and the proxy would
     * skip it anyway. It is not wanted either — each repository.save() is already its own
     * transaction, so one bad recipient cannot roll back the notifications that did work.
     */
    private void deliver(NotificationRequest request) {
        NotificationTemplate template = templates.get(request.templateId());
        Map<String, String> variables = request.variables();

        requireDeclaredVariables(template, variables);

        String link = renderer.render(template.config().link(), variables);

        /*
          Only the caller-supplied variables are stored. The built-ins are re-derived on read so a
          change of domain does not leave old notifications pointing at the previous one.
         */
        String variablesJson = writeVariables(variables);
        Map<String, String> withBuiltIns = new HashMap<>(variables);
        withBuiltIns.put("link", link);
        withBuiltIns.put("appUrl", appUrl);
        Set<String> recipients = resolveRecipients(template, request);

        if (recipients.isEmpty()) {
            log.warn("Template '{}' resolved no recipients from parties {} — nothing sent.",
                    template.id(), request.parties().keySet());
            return;
        }

        for (String channelKey : template.config().channels()) {
            NotificationChannel channel = NotificationChannel.fromKey(channelKey);
            switch (channel) {
                case IN_APP -> recipients.forEach(
                        userId -> saveInApp(template, userId, variablesJson, link));
                case MAIL -> mail.send(template, recipients, withBuiltIns);
                case PUSH -> log.info(
                        "Template '{}' lists PUSH; Web Push is not implemented yet.", template.id());
            }
        }
    }


    private void saveInApp(NotificationTemplate template, String recipientId,
                           String variablesJson, String link) {
        repository.save(Notification.builder()
                .id(idGenerator.next(IdGeneratorService.NOTIFICATION, "ntf-"))
                .recipientId(recipientId)
                .templateId(template.id())
                .channel(NotificationChannel.IN_APP.getCode())
                .variablesJson(variablesJson)
                .link(link)
                .build());
    }

    /**
     * Turns the config's recipient names into user ids using the parties the caller named.
     *
     * <p>A name the caller did not supply is skipped with a warning rather than failing the whole
     * dispatch: a template that also notifies a manager should still reach the learner on a journey
     * that happens to have no manager.
     */
    private Set<String> resolveRecipients(NotificationTemplate template, NotificationRequest request) {
        Set<String> userIds = new LinkedHashSet<>();
        for (String party : template.config().recipients()) {
            String userId = request.parties().get(party);
            if (userId == null || userId.isBlank()) {
                log.warn("Template '{}' wants recipient '{}' but the request did not name one.",
                        template.id(), party);
                continue;
            }
            userIds.add(userId);
        }
        return userIds;
    }

    private void requireDeclaredVariables(NotificationTemplate template, Map<String, String> variables) {
        for (String declared : template.config().variables()) {
            if (!variables.containsKey(declared)) {
                throw new IllegalArgumentException("Template '" + template.id()
                        + "' declares variable '" + declared + "' but the caller did not supply it.");
            }
        }
    }

    private String writeVariables(Map<String, String> variables) {
        try {
            return mapper.writeValueAsString(variables);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise notification variables", e);
        }
    }
}
