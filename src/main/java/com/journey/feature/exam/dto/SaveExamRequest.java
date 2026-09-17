package com.journey.feature.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveExamRequest(

        @NotBlank
        String title,

        @NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100)
        Integer passingScorePercent,

        /** Ignored — the author is the signed-in caller. Kept so existing clients still deserialize. */
        String createdById,

        /** Ignored, as above. */
        String createdByName,

        List<QuestionDraftDto> questions
) {
}
