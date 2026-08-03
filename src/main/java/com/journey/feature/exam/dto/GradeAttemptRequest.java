package com.journey.feature.exam.dto;

import java.util.List;

public record GradeAttemptRequest(

        List<QuestionMarkDto> marks,

        Boolean passed,

        String gradedById,

        String gradedByName
) {
}