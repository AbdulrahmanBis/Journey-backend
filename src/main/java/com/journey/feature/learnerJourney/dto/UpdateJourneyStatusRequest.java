package com.journey.feature.learnerJourney.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateJourneyStatusRequest(@NotNull String status) {}
