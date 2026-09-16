package com.journey.feature.learnerJourney.event;

/**
 * Raised when a learner journey item's status changes.
 *
 * <p>Status wording is carried in both languages. A status is one of our 1001-based enums, which
 * owns an English and an Arabic label, and the notification is rendered in whichever language the
 * reader is using — so a single already-chosen string would come out wrong in one of them.
 */
public record ItemStatusChangedEvent(
        String learnerJourneyId,
        String journeyTitle,
        String itemTitle,
        String statusEnglish,
        String statusArabic,
        String learnerId,
        /** Whoever made the change; may be null if the client did not say. The listener resolves
            their display name — the service has no business loading users. */
        String actorId
) {}
