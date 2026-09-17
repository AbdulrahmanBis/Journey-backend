package com.journey.feature.notification.controller;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.feature.notification.dto.NotificationDto;
import com.journey.feature.notification.dto.NotificationPageDto;
import com.journey.feature.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The caller's own notifications. There is deliberately no endpoint that takes a user id — the
 * recipient is always the authenticated principal, so one user cannot read another's bell.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;
    private final com.journey.feature.notification.service.MailNotificationSender mail;
    private final com.journey.common.security.AccessPolicy access;

    /** Full history, paged — backs the /notifications page. */
    @GetMapping
    public ResponseEntity<NotificationPageDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(
                notifications.list(currentUserId(), page, size, language(lang, acceptLanguage)));
    }

    /** The dropdown: most recent few plus the badge count. */
    @GetMapping("/recent")
    public ResponseEntity<NotificationPageDto> recent(
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(notifications.recent(currentUserId(), language(lang, acceptLanguage)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("unreadCount", notifications.unreadCount(currentUserId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markRead(
            @PathVariable String id,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(
                notifications.markRead(id, currentUserId(), language(lang, acceptLanguage)));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        return ResponseEntity.ok(Map.of("marked", notifications.markAllRead(currentUserId())));
    }

    /** GET /api/notifications/mail-status — is email on and configured (Admin). No secrets are returned. */
    @GetMapping("/mail-status")
    public ResponseEntity<Map<String, Object>> mailStatus() {
        access.requireRole(com.journey.common.enums.UserRole.ADMIN);
        return ResponseEntity.ok(mail.status());
    }

    /** POST /api/notifications/test-email — sends a test email to the calling Admin now; 409 with the reason if it fails. */
    @org.springframework.web.bind.annotation.PostMapping("/test-email")
    public ResponseEntity<Map<String, String>> testEmail() {
        access.requireRole(com.journey.common.enums.UserRole.ADMIN);
        return ResponseEntity.ok(Map.of("sentTo", mail.sendTest(currentUserId())));
    }

    /** An explicit {@code ?lang=} wins, because the UI switcher is what the reader actually set. */
    private String language(String lang, String acceptLanguage) {
        return lang != null && !lang.isBlank() ? lang : acceptLanguage;
    }

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED);
        }
        return auth.getName();
    }
}
