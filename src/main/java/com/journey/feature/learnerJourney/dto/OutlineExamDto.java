package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

/**
 * @param open the learner has finished every unit (items and quizzes), so the exam can be taken
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutlineExamDto(
        String examId,
        String title,
        boolean open,
        EnumValueDto attemptStatus,
        Integer scorePercent,
        Boolean passed
) {}
