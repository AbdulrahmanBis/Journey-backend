package com.journey.feature.learnerJourney.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code actorRole} is the UserRole code (1001-1004). */
public record AddNoteRequest(
        @NotBlank String message,
        String actorId,
        String actorName,
        Integer actorRole
) {}
