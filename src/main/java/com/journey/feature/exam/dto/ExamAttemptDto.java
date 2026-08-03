package com.journey.feature.exam.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ExamAttemptDto(

        String id,

        String learnerJourneyId,

        String examId,

        Integer status,

        List<ExamAnswerDto> answers,

        LocalDateTime submittedAt,

        LocalDateTime gradedAt,

        String gradedById,

        String gradedByName,

        Integer scorePercent,

        Boolean passed
) {
}