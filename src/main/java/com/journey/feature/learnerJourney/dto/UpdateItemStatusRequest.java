package com.journey.feature.learnerJourney.dto;

import jakarta.validation.constraints.NotNull;

/** {@code status} is the ItemStatus code (1001-1005). */
public record UpdateItemStatusRequest(
        @NotNull Integer status,
        Double timeSpentHours,  // required when status = COMPLETED
        String actorId
) {}
