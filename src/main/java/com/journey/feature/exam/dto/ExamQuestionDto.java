package com.journey.feature.exam.dto;

import com.journey.common.dto.EnumValueDto;

import java.util.List;

public record ExamQuestionDto(

        String id,

        EnumValueDto type,

        String prompt,

        List<String> options,

        Integer correctOptionIndex,

        Boolean correctBoolAnswer
) {
}
