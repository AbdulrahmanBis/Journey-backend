package com.journey.feature.journey.dto;

import java.time.LocalDateTime;

public record JourneyDto(
        String id,
        String title,
        String description,
        String techTag,
        Integer targetDays,
        String createdById,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}