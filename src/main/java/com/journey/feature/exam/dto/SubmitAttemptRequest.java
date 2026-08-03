package com.journey.feature.exam.dto;

import java.util.List;

public record SubmitAttemptRequest(

        String examId,

        List<AnswerDraftDto> answers
) {
}