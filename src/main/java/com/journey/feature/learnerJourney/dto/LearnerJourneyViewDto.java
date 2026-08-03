package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.feature.journey.dto.JourneyDto;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearnerJourneyViewDto(
        // LearnerJourney fields
        String id,
        String journeyId,
        String learnerId,
        String assignedById,
        String assignedByName,
        LocalDateTime assignedAt,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        // Composed / computed fields
        JourneyDto journey,
        List<JourneyItemWithProgressDto> items,
        int percentComplete,
        double totalTimeSpentHours
) {}
