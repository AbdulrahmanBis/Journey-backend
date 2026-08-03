package com.journey.feature.learnerJourney.dto;

import com.journey.common.enums.ItemStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateItemStatusRequest(
        @NotNull String status,
        Double timeSpentHours,  // required when status = COMPLETED
        String actorId
) {}
