package com.journey.feature.journeyPackage.dto;

import com.journey.common.dto.EnumValueDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A package as one learner has it.
 *
 * @param status          NEW until a journey starts, REFLECT while in progress, COMPLETED when every
 *                        journey not cancelled is complete, CANCELLED when the package was cancelled
 * @param percentComplete average of the journeys that aren't cancelled
 */
public record PackageAssignmentDto(
        String id,
        String packageId,
        String title,
        String description,
        String learnerId,
        String assignedById,
        String assignedByName,
        LocalDateTime assignedAt,
        boolean selfEnrolled,
        LocalDateTime cancelledAt,
        EnumValueDto status,
        int percentComplete,
        int completedJourneys,
        int totalJourneys,
        List<PackageJourneyProgressDto> journeys
) {}
