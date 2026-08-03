package com.journey.feature.journey.dto;

public record JourneyItemDto(
        String id,
        String journeyId,
        String title,
        String description,
        Integer order
) {}