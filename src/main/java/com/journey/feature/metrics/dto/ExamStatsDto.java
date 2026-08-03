package com.journey.feature.metrics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExamStatsDto(
        int configured,
        int taken,
        int underReview,
        int graded,
        int passed,
        int failed,
        Double avgScorePercent
) {}
