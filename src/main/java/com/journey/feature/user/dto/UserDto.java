package com.journey.feature.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.enums.UserRole;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserDto(
        String id,
        String name,
        String email,
        String role,
        String seniorId,
        LocalDateTime createdAt
) {}
