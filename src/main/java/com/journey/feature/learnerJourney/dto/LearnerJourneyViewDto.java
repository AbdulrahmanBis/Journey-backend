package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;
import com.journey.feature.exam.dto.ExamAttemptDto;
import com.journey.feature.exam.dto.ExamDto;
import com.journey.feature.journey.dto.JourneyDto;

import java.time.LocalDate;
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
        /** Null when there is no deadline. */
        LocalDate dueDate,
        /** Enrolled from the catalog; assignedBy is then the reviewer. */
        boolean selfEnrolled,
        EnumValueDto status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        // Composed / computed fields
        JourneyDto journey,
        List<JourneyItemWithProgressDto> items,
        int percentComplete,
        double totalTimeSpentHours,
        /** Present when the journey template has an exam configured. */
        ExamDto exam,
        /** Present once the learner has submitted an attempt. */
        ExamAttemptDto examAttempt,
        /** The journey's units with this learner's status on each. */
        List<UnitProgressSummaryDto> units
) {}
