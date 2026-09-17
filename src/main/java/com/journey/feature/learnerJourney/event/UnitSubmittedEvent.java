package com.journey.feature.learnerJourney.event;

/** A unit's items and quiz are done and it is waiting for the reviewer. */
public record UnitSubmittedEvent(
        String learnerJourneyId,
        String journeyTitle,
        String unitTitle,
        String learnerId,
        String assignerId
) {}
