package com.journey.feature.announcement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Someone closed an announcement on their dashboard. It stays readable; it just no longer shows there. */
@Entity
@Table(name = "announcement_dismissals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementDismissal {

    @EmbeddedId
    private Key id;

    @Column(name = "dismissed_at", nullable = false)
    private LocalDateTime dismissedAt = LocalDateTime.now();

    public AnnouncementDismissal(String announcementId, String userId) {
        this(new Key(announcementId, userId), LocalDateTime.now());
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        @Column(name = "announcement_id", length = 36)
        private String announcementId;

        @Column(name = "user_id", length = 36)
        private String userId;
    }
}
