package com.journey.feature.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;
import com.journey.feature.journeyPackage.dto.PackageAssignmentDto;
import com.journey.feature.learnerJourney.dto.LearnerJourneyViewDto;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearnerSummaryDto(
        String id,
        String name,
        String email,
        EnumValueDto role,
        String seniorId,
        LocalDateTime createdAt,
        List<LearnerJourneyViewDto> journeys,
        /** Packages grouping some of {@code journeys}; each journey still appears in that list too. */
        List<PackageAssignmentDto> packages
) {}
