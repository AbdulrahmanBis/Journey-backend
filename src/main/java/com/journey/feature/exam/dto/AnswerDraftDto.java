package com.journey.feature.exam.dto;

public record AnswerDraftDto(

        String questionId,

        Integer selectedOptionIndex,

        Boolean boolAnswer,

        String openText
) {
}