package com.journey.feature.notification.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.journey.common.enums.NotificationChannel;
import com.journey.feature.notification.dto.NotificationDto;
import com.journey.feature.notification.dto.NotificationPageDto;
import com.journey.feature.notification.entity.Notification;
import com.journey.feature.notification.repository.NotificationRepository;
import com.journey.feature.notification.template.NotificationTemplate;
import com.journey.feature.notification.template.TemplateRegistry;
import com.journey.feature.notification.template.TemplateRenderer;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read side of notifications: listing, counting and marking read.
 *
 * <p>Every method takes the caller's id and scopes to it. A notification belongs to exactly one
 * person, so there is no "read someone else's" path to get wrong — the id is never taken from the
 * request body.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository repository;
    private final TemplateRegistry templates;
    private final TemplateRenderer renderer;
    private final ObjectMapper mapper;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String appUrl;

    public NotificationPageDto list(String recipientId, int page, int size, String language) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Notification> found = repository.findByRecipientIdOrderByCreatedAtDesc(
                recipientId, PageRequest.of(Math.max(page, 0), safeSize));

        return new NotificationPageDto(
                found.getContent().stream().map(n -> toDto(n, language)).toList(),
                repository.countByRecipientIdAndReadAtIsNull(recipientId),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages());
    }

    /** The dropdown's contents — the most recent few, with the badge count. */
    public NotificationPageDto recent(String recipientId, String language) {
        List<Notification> found = repository.findTop10ByRecipientIdOrderByCreatedAtDesc(recipientId);
        long unread = repository.countByRecipientIdAndReadAtIsNull(recipientId);
        return new NotificationPageDto(
                found.stream().map(n -> toDto(n, language)).toList(),
                unread, 0, found.size(), found.size(), 1);
    }

    public long unreadCount(String recipientId) {
        return repository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Transactional
    public NotificationDto markRead(String id, String recipientId, String language) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        // Not found rather than forbidden: whether someone else's notification exists is not the
        // caller's business.
        if (!notification.getRecipientId().equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            repository.save(notification);
        }
        return toDto(notification, language);
    }

    /** @return how many were still unread. */
    @Transactional
    public int markAllRead(String recipientId) {
        return repository.markAllRead(recipientId, LocalDateTime.now());
    }

    // ─── Rendering ────────────────────────────────────────────────────────────

    /**
     * Renders the stored template and variables into the requested language.
     *
     * <p>Rendering is best-effort on purpose. A template that was deleted or edited into an
     * incompatible shape must not take down the whole notifications list — the row degrades to its
     * template id and everything else still renders.
     */
    private NotificationDto toDto(Notification n, String language) {
        String title = n.getTemplateId();
        String body = "";

        try {
            NotificationTemplate template = templates.get(n.getTemplateId());
            NotificationTemplate.MessageContent content = template.inAppFor(resolveLanguage(language));
            Map<String, String> variables = new HashMap<>(readVariables(n));
            variables.put("link", n.getLink() == null ? "" : n.getLink());
            variables.put("appUrl", appUrl);
            title = renderer.render(content.title(), variables);
            body = renderer.render(content.body(), variables);
        } catch (RuntimeException e) {
            log.warn("Could not render notification {} (template {}): {}",
                    n.getId(), n.getTemplateId(), e.getMessage());
        }

        return new NotificationDto(
                n.getId(),
                n.getTemplateId(),
                NotificationChannel.fromCode(n.getChannel()).toDto(),
                title,
                body,
                n.getLink(),
                n.getReadAt() != null,
                n.getCreatedAt());
    }

    private Map<String, String> readVariables(Notification n) {
        if (n.getVariablesJson() == null || n.getVariablesJson().isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(n.getVariablesJson(), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Unreadable variables on notification {}: {}", n.getId(), e.getMessage());
            return Map.of();
        }
    }

    /** Accepts {@code ar}, {@code ar-SA}, {@code AR}; anything unknown falls back to English. */
    static String resolveLanguage(String language) {
        if (language == null || language.isBlank()) {
            return TemplateRegistry.DEFAULT_LANGUAGE;
        }
        String tag = language.trim().toLowerCase().split("[-_]")[0];
        return tag.equals("ar") ? "ar" : TemplateRegistry.DEFAULT_LANGUAGE;
    }
}
