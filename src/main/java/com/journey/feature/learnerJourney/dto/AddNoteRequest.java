package com.journey.feature.learnerJourney.dto;

import com.journey.common.enums.UserRole;
import jakarta.validation.constraints.NotBlank;

public record AddNoteRequest(
        @NotBlank String message,
        String actorId,
        String actorName,
        String actorRole
) {}
