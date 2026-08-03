package com.journey.feature.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.enums.UserRole;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeniorSummaryDto(
        String id,
        String name,
        String email,
        UserRole role,
        LocalDateTime createdAt,
        List<LearnerSummaryDto> learners
) {}
