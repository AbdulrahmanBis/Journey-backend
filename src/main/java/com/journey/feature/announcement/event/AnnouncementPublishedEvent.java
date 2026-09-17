package com.journey.feature.announcement.event;

import java.util.List;

/** An announcement was posted, or edited with "notify again". Everyone in {@code recipientIds} hears about it. */
public record AnnouncementPublishedEvent(
        String announcementId,
        String title,
        String excerpt,
        String authorName,
        List<String> recipientIds
) {}
