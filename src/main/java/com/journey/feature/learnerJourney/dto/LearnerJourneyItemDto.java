package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearnerJourneyItemDto(
        String id,
        String learnerJourneyId,
        String journeyItemId,
        EnumValueDto status,
        Double timeSpentHours,
        LocalDateTime updatedAt,
        List<NoteDto> notes
) {}
