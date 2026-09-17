package com.journey.feature.team.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

import java.time.LocalDate;

/** The journey a learner touched most recently, among those still open. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CurrentJourneyDto(
        String learnerJourneyId,
        String title,
        int percentComplete,
        EnumValueDto status,
        LocalDate dueDate
) {}
