package com.journey.feature.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.enums.UserRole;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateUserRequest(
        String name,
        String email,
        String password,
        UserRole role,
        String seniorId
) {}
