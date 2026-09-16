package com.journey.feature.journeyPackage.dto;

/**
 * Where a learner journey sits within one package the learner has, for "next journey" navigation.
 *
 * @param nextLearnerJourneyId the next unfinished journey after this one (wrapping to the start), or
 *                             null when everything else in the package is finished
 */
public record PackageContextDto(
        String packageAssignmentId,
        String packageTitle,
        int position,
        int total,
        int completedJourneys,
        String nextLearnerJourneyId,
        String nextJourneyTitle
) {}
