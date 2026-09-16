package com.journey.feature.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

/**
 * The signed-in learner's standing with a catalog entry.
 *
 * @param learnerJourneyId    for a journey: the live assignment, or the last cancelled one
 * @param packageAssignmentId for a package: likewise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyProgressDto(
        EnumValueDto status,
        int percentComplete,
        String learnerJourneyId,
        String packageAssignmentId
) {}
