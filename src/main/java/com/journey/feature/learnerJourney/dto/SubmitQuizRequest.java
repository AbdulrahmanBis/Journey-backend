package com.journey.feature.learnerJourney.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubmitQuizRequest(@NotEmpty List<Answer> answers) {

    /** {@code selectedOptionIndex} is a 1001-based option code, as in exams. */
    public record Answer(@NotNull String questionId, Integer selectedOptionIndex, Boolean boolAnswer) {}
}
