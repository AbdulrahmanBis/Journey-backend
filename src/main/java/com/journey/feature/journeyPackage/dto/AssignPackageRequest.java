package com.journey.feature.journeyPackage.dto;

import jakarta.validation.constraints.NotBlank;

/** The assigner is the signed-in caller. */
public record AssignPackageRequest(
        @NotBlank String packageId,
        @NotBlank String learnerId
) {}
