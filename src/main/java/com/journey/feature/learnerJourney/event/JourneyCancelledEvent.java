package com.journey.feature.learnerJourney.event;

/** Raised when a journey a learner was working on is cancelled out from under them. */
public record JourneyCancelledEvent(
        String learnerJourneyId,
        String journeyTitle,
        String learnerId
) {}
