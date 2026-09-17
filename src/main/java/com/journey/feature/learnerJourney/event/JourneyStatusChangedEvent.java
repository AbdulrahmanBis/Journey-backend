package com.journey.feature.learnerJourney.event;

/**
 * A learner journey's overall status changed (completed, reopened, cancelled…), by recomputation or by a
 * manager's override. Published inside the transaction so listeners can keep derived records — certificates —
 * in step with it.
 */
public record JourneyStatusChangedEvent(String learnerJourneyId, int status) {}
