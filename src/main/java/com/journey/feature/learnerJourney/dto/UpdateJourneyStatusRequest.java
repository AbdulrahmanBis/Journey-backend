package com.journey.feature.learnerJourney.dto;

import jakarta.validation.constraints.NotNull;

/** {@code status} is the ItemStatus code (1001-1005). */
public record UpdateJourneyStatusRequest(@NotNull Integer status) {}
