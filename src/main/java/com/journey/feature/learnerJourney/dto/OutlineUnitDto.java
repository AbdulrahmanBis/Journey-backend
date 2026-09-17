package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

/**
 * A unit in the outline.
 *
 * @param readyForReview every item done and the quiz (if any) answered — the learner can send it for review
 * @param notes          the unit-level thread (review feedback)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutlineUnitDto(
        String learnerUnitId,
        String unitId,
        String title,
        String description,
        int order,
        EnumValueDto status,
        int completedItems,
        int totalItems,
        OutlineQuizDto quiz,
        boolean readyForReview,
        java.util.List<NoteDto> notes,
        java.util.List<OutlineItemDto> items
) {}
