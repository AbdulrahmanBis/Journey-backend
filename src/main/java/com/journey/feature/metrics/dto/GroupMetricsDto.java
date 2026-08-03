package com.journey.feature.metrics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupMetricsDto(
        int learnerCount,
        int totalJourneys,
        int activeJourneys,
        int completedJourneys,
        double totalHours,
        int avgCompletionPercent,
        ExamStatsDto exams,
        List<HoursBucketDto> hoursByDay,
        List<HoursBucketDto> hoursByMonth,
        List<HoursBucketDto> hoursByQuarter,
        List<PerLearnerRow> perLearner
) {
    public record PerLearnerRow(
            String learnerId,
            String learnerName,
            int activeJourneys,
            int completedJourneys,
            double totalHours,
            int avgCompletionPercent,
            int examsPassed,
            int examsFailed
    ) {}
}
