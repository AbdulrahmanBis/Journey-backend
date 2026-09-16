package com.journey.feature.journeyPackage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code journeyIds} in the order the learner should take them. */
public record SavePackageRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotEmpty List<String> journeyIds
) {}
