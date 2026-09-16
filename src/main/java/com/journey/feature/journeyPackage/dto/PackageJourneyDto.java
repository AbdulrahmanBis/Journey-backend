package com.journey.feature.journeyPackage.dto;

/** A journey inside a package definition. */
public record PackageJourneyDto(
        String journeyId,
        String title,
        String techTag,
        int position
) {}
