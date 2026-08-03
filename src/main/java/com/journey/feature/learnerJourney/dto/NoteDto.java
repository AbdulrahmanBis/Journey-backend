package com.journey.feature.learnerJourney.dto;

import com.journey.common.enums.UserRole;

import java.time.LocalDateTime;

public record NoteDto(
        String id,
        String actorId,
        String actorName,
        String actorRole,
        String message,
        LocalDateTime timestamp
) {}
