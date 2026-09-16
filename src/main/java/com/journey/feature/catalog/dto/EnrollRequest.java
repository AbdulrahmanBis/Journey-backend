package com.journey.feature.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** @param type a CatalogItemType code */
public record EnrollRequest(
        @NotNull Integer type,
        @NotBlank String id
) {}
