package com.journey.feature.journey.dto;

import java.time.LocalDateTime;

public record JourneyDto(
        String id,
        String title,
        String description,
        String techTag,
        String createdById,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}