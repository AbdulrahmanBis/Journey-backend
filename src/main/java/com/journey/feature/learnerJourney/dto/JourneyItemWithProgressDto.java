package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JourneyItemWithProgressDto(
        // from JourneyItem (template)
        String id,
        String journeyId,
        String title,
        String description,
        Integer order,
        // learner-specific progress
        LearnerJourneyItemDto progress
) {}
