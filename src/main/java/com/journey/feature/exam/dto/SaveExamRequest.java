package com.journey.feature.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveExamRequest(

        @NotBlank
        String title,

        @NotNull
        Integer passingScorePercent,

        @NotBlank
        String createdById,

        @NotBlank
        String createdByName,

       List<QuestionDraftDto> questions
) {
}