package com.journey.feature.notification.dto;

import com.journey.common.dto.EnumValueDto;

import java.time.LocalDateTime;

/**
 * A notification as the bell and the notifications page see it.
 *
 * <p>{@code title} and {@code body} are rendered server-side in the caller's language, so the front
 * end never needs the template files or the variable values.
 */
public record NotificationDto(
        String id,
        String templateId,
        EnumValueDto channel,
        String title,
        String body,
        String link,
        boolean read,
        LocalDateTime createdAt
) {}
