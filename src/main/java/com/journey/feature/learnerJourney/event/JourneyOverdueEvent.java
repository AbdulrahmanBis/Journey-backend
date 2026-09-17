package com.journey.feature.learnerJourney.event;

import java.time.LocalDate;

/** An open journey has passed its due date. */
public record JourneyOverdueEvent(
        String learnerJourneyId,
        String journeyTitle,
        String learnerId,
        String assignerId,
        LocalDate dueDate
) {}
