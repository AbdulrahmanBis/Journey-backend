package com.journey.feature.notification.dto;

import java.util.List;

/**
 * A page of notifications plus the unread count, so the bell can update its badge from the same
 * response that fills its list instead of making a second call.
 */
public record NotificationPageDto(
        List<NotificationDto> items,
        long unreadCount,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
