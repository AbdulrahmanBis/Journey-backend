package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutlineQuizDto(
        int questionCount,
        boolean answered,
        Integer scorePercent
) {}
