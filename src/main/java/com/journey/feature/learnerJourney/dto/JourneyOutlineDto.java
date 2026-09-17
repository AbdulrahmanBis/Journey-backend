package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

/**
 * The journey log's left panel and header: units and items with statuses, but no item content —
 * that is fetched one item at a time.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JourneyOutlineDto(
        String learnerJourneyId,
        String learnerId,
        String learnerName,
        com.journey.feature.journey.dto.JourneyDto journey,
        EnumValueDto status,
        int percentComplete,
        double totalTimeSpentHours,
        java.time.LocalDate dueDate,
        boolean selfEnrolled,
        String assignedById,
        String assignedByName,
        java.util.List<OutlineUnitDto> units,
        OutlineExamDto exam
) {}
