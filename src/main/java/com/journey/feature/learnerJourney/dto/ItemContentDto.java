package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

/**
 * One item with its content, loaded when the learner (or reviewer) opens it.
 *
 * @param previousProgressId / nextProgressId neighbours across units, for Previous / Next
 * @param lastInUnit the next step is the unit's quiz, or the next unit
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItemContentDto(
        String progressId,
        String journeyItemId,
        String learnerUnitId,
        String unitTitle,
        String title,
        String description,
        java.util.List<com.journey.feature.journey.dto.AttachmentDto> attachments,
        EnumValueDto status,
        Double timeSpentHours,
        java.util.List<NoteDto> notes,
        String previousProgressId,
        String nextProgressId,
        String nextTitle,
        boolean lastInUnit
) {}
