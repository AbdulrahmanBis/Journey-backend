package com.journey.feature.exam.dto;

public record ExamAnswerDto(

        String questionId,

        Integer selectedOptionIndex,

        Boolean boolAnswer,

        String openText,

        Boolean markedCorrect
) {
}