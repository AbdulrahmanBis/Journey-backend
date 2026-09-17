package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

/**
 * A quiz question as the learner sees it. The correct answer and {@code correct} are only filled in once
 * the quiz has been submitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizQuestionDto(
        String id,
        EnumValueDto type,
        String prompt,
        java.util.List<String> options,
        Integer selectedOptionIndex,
        Boolean boolAnswer,
        Boolean correct,
        Integer correctOptionIndex,
        Boolean correctBoolAnswer
) {}
