package com.journey.feature.announcement.event;

/** An announcement was deleted; its notifications go with it. */
public record AnnouncementDeletedEvent(String announcementId) {}
