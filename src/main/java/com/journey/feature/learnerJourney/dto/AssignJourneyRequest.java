package com.journey.feature.learnerJourney.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignJourneyRequest(
        @NotBlank String journeyId,
        @NotBlank String learnerId,
        String assignedById,
        String assignedByName
) {}
