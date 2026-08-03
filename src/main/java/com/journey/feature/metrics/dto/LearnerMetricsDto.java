package com.journey.feature.metrics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearnerMetricsDto(
        String learnerId,
        String learnerName,
        int totalJourneys,
        int activeJourneys,
        int completedJourneys,
        int cancelledJourneys,
        int totalItems,
        int completedItems,
        int itemCompletionPercent,
        double totalHours,
        Double avgHoursPerCompletedItem,
        ExamStatsDto exams,
        List<HoursBucketDto> hoursByDay,
        List<HoursBucketDto> hoursByMonth,
        List<HoursBucketDto> hoursByQuarter
) {}
