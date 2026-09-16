package com.journey.feature.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserDto(
        String id,
        String name,
        String email,
        EnumValueDto role,
        String seniorId,
        LocalDateTime createdAt
) {}
