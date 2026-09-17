package com.journey.feature.learnerJourney.event;

import java.time.LocalDate;

/** An open journey is due within the reminder window. */
public record JourneyDueSoonEvent(
        String learnerJourneyId,
        String journeyTitle,
        String learnerId,
        LocalDate dueDate,
        long daysLeft
) {}
