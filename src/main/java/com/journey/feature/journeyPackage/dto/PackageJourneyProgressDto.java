package com.journey.feature.journeyPackage.dto;

import com.journey.common.dto.EnumValueDto;

/** One learner journey inside a package assignment, with enough progress to draw it. */
public record PackageJourneyProgressDto(
        String learnerJourneyId,
        String journeyId,
        String title,
        String techTag,
        int position,
        EnumValueDto status,
        int percentComplete
) {}
