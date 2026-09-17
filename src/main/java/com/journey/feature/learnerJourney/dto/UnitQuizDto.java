package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnitQuizDto(
        String learnerUnitId,
        String unitTitle,
        java.util.List<QuizQuestionDto> questions,
        boolean submitted,
        Integer scorePercent,
        java.time.LocalDateTime submittedAt
) {}
