package com.journey.feature.journeyPackage.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PackageDto(
        String id,
        String title,
        String description,
        String createdById,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PackageJourneyDto> journeys,
        /** Assignments not cancelled. While any assignment exists at all, the package can't be deleted. */
        long activeAssignments,
        boolean deletable
) {}
