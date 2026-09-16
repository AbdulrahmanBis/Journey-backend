package com.journey.feature.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Every field is optional; null leaves it unchanged.
 * {@code role} is the UserRole code (1001-1005).
 *
 * <p>{@code seniorId} cannot be cleared through this record, because null means "unchanged". Use
 * {@code clearSenior} for that.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateUserRequest(
        String name,
        String email,
        String password,
        Integer role,
        String departmentId,
        String seniorId,
        Boolean clearSenior
) {}
