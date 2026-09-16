package com.journey.feature.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Where to send the learner next: the new journey log, or (for a package) its first journey. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrollResultDto(
        String learnerJourneyId,
        String packageAssignmentId
) {}
