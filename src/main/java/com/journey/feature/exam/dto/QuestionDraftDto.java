package com.journey.feature.exam.dto;

import java.util.List;

public record QuestionDraftDto(

        String id,

        Integer type,

        String prompt,

        List<String> options,

        Integer correctOptionIndex,

        Boolean correctBoolAnswer
) {
}