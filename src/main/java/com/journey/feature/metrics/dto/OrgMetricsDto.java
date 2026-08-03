package com.journey.feature.metrics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrgMetricsDto(
        // All GroupMetrics fields
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
        List<GroupMetricsDto.PerLearnerRow> perLearner,
        // Org-only extras
        int seniorCount,
        List<PerSeniorRow> perSenior
) {
    public record PerSeniorRow(
            String seniorId,
            String seniorName,
            int learnerCount,
            double totalHours,
            int avgCompletionPercent,
            int examsPassed,
            int examsFailed
    ) {}
}
