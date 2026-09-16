package com.journey.feature.learnerJourney.dto;

import com.journey.common.dto.EnumValueDto;

import java.time.LocalDateTime;

public record NoteDto(
        String id,
        String actorId,
        String actorName,
        EnumValueDto actorRole,
        String message,
        LocalDateTime timestamp
) {}
