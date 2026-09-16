package com.journey.feature.journeyPackage.event;

public record PackageCancelledEvent(
        String packageAssignmentId,
        String learnerId,
        String packageTitle
) {}
