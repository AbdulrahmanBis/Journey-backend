package com.journey.feature.notification.repository;

import com.journey.feature.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    List<Notification> findTop10ByRecipientIdOrderByCreatedAtDesc(String recipientId);

    long countByRecipientIdAndReadAtIsNull(String recipientId);

    /**
     * Marks everything unread as read in one statement.
     *
     * <p>The {@code read_at IS NULL} guard means re-running it cannot rewrite the timestamp of
     * something already read.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    int markAllRead(@Param("recipientId") String recipientId, @Param("now") LocalDateTime now);
}
