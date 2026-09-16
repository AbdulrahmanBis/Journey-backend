package com.journey.feature.exam.event;

/**
 * Raised when a learner submits an exam attempt for review.
 *
 * <p>Deliberately thin: the exam feature knows about attempts, not about who a learner reports to.
 * The listener resolves the people involved from the learner journey.
 */
public record ExamSubmittedEvent(
        String learnerJourneyId,
        String attemptId
) {}
