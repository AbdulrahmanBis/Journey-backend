package com.journey.feature.journeyPackage.event;

/** Values, not entities: listeners run after commit, possibly on another thread. */
public record PackageAssignedEvent(
        String packageAssignmentId,
        String learnerId,
        String assignerId,
        String assignerName,
        String packageTitle,
        int journeyCount
) {}
