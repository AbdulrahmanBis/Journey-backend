package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

/** A learner's unit in brief, for dashboards and journey views. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnitProgressSummaryDto(
        String learnerUnitId,
        String unitId,
        String title,
        int order,
        EnumValueDto status,
        int completedItems,
        int totalItems,
        boolean hasQuiz,
        boolean quizAnswered,
        java.time.LocalDateTime updatedAt
) {}
