package com.journey.feature.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One delivered in-app notification.
 *
 * <p>The rendered wording is <em>not</em> stored. The row keeps the template id and the variables
 * it was raised with, and the text is rendered on read in whichever language the reader is using.
 * Storing rendered text would freeze a notification into the language it was sent in, which is
 * wrong for an app with a live language switcher — a learner who flips to English would still see
 * Arabic in the bell.
 *
 * <p>The trade-off is that editing a template's wording also changes how existing notifications
 * read. That is usually what you want from a typo fix, and the alternative costs correct i18n.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_recipient", columnList = "recipient_id, created_at"),
        @Index(name = "idx_notifications_unread", columnList = "recipient_id, read_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "recipient_id", nullable = false, length = 36)
    private String recipientId;

    /** Directory name under the templates root. */
    @Column(name = "template_id", nullable = false, length = 64)
    private String templateId;

    /** NotificationChannel code. In-app is the only channel that persists a row. */
    @Column(nullable = false)
    private Integer channel;

    /** The {@code ${...}} values, as JSON, used to render the text on read. */
    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;

    /** Where clicking the notification goes — a front-end route, already rendered. */
    @Column(name = "link", length = 500)
    private String link;

    /** Null until the recipient reads it. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
