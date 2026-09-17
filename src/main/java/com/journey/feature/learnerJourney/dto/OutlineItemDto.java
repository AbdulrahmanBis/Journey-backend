package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

/** An item line in the outline: no content, just enough to navigate. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutlineItemDto(
        String progressId,
        String journeyItemId,
        String title,
        int order,
        EnumValueDto status,
        Double timeSpentHours,
        int noteCount
) {}
