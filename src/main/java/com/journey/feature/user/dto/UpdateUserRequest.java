package com.journey.feature.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code role} is the UserRole code (1001-1004), or null to leave unchanged. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateUserRequest(
        String name,
        String email,
        String password,
        Integer role,
        String seniorId
) {}
