package com.journey.feature.learnerJourney.event;

/** A reviewer moved a learner's unit (completed it, or sent it back). Both labels travel, as for items. */
public record UnitStatusChangedEvent(
        String learnerJourneyId,
        String journeyTitle,
        String unitTitle,
        String statusEnglish,
        String statusArabic,
        String learnerId,
        String actorId
) {}
