package com.journey.feature.exam.event;

/** Raised when a reviewer grades a submitted exam attempt. */
public record ExamGradedEvent(
        String learnerJourneyId,
        String attemptId,
        Integer scorePercent,
        boolean passed,
        String graderName
) {}
