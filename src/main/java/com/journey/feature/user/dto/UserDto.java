package com.journey.feature.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;
import com.journey.feature.department.dto.DepartmentDto;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserDto(
        String id,
        String name,
        String email,
        EnumValueDto role,
        /** Every person has one. {@code memberCount} is left out here — it describes the department, not the person. */
        DepartmentDto department,
        String seniorId,
        LocalDateTime createdAt,
        /** Intro guide version this person dismissed for good; absent if never. */
        Integer introSeenVersion
) {}
