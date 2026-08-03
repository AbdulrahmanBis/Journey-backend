package com.journey.feature.exam.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ExamDto(

        String id,

        String journeyId,

        String title,

        Integer passingScorePercent,

        String createdById,

        String createdByName,

        LocalDateTime updatedAt,

        List<ExamQuestionDto> questions
) {
}