package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.enums.ItemStatus;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearnerJourneyItemDto(
        String id,
        String learnerJourneyId,
        String journeyItemId,
        String status,
        Double timeSpentHours,
        LocalDateTime updatedAt,
        List<NoteDto> notes
) {}
