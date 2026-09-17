package com.journey.feature.journey.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.feature.exam.dto.ExamQuestionDto;

import java.util.List;

/**
 * A journey as a learner would go through it, without an assignment — for the preview page.
 *
 * <p>Staff get everything, quiz and exam answers included, so they can try it out. A learner deciding
 * whether to enroll gets the first unit's content as a sample; later units list only their item titles,
 * and quiz and exam questions are never included for them.
 *
 * @param limited true when content beyond the first unit, and all questions, were left out
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JourneyPreviewDto(
        JourneyDto journey,
        boolean limited,
        List<Unit> units,
        Exam exam
) {

    /** @param locked its items' content was left out */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Unit(
            String id,
            String title,
            String description,
            int order,
            boolean locked,
            List<JourneyItemDto> items,
            int quizQuestionCount,
            /** Staff only. */
            List<ExamQuestionDto> quiz
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Exam(
            String title,
            Integer passingScorePercent,
            int questionCount,
            /** Staff only. */
            List<ExamQuestionDto> questions
    ) {}
}
