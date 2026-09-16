package com.journey.feature.learnerJourney.event;

/**
 * Raised the moment a learner journey's last item is completed — on the transition only, not on
 * every recomputation, so it cannot fire twice for the same journey.
 */
public record JourneyCompletedEvent(
        String learnerJourneyId,
        String journeyTitle,
        String learnerId,
        String assignerId
) {}
