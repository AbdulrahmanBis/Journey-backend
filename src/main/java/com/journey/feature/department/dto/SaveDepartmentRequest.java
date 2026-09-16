package com.journey.feature.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveDepartmentRequest(
        @NotBlank @Size(max = 120) String english,
        @NotBlank @Size(max = 120) String arabic
) {}
