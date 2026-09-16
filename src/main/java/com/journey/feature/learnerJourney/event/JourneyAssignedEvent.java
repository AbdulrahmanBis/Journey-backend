package com.journey.feature.learnerJourney.event;

/**
 * Raised when a journey has been assigned to a learner.
 *
 * <p>Carries values rather than entities. Listeners run after the transaction commits, possibly on
 * another thread, where a lazily-loaded entity would be a detached-entity bug waiting to happen.
 */
public record JourneyAssignedEvent(
        String learnerJourneyId,
        String learnerId,
        String assignerId,
        String assignerName,
        String journeyTitle
) {}
