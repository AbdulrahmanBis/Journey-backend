package com.journey.feature.team.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;
import com.journey.feature.department.dto.DepartmentDto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of the team table.
 *
 * @param averageProgress average over open journeys (0 when there are none)
 * @param nextDueDate     earliest due date among open journeys
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamLearnerDto(
        String id,
        String name,
        String email,
        DepartmentDto department,
        String seniorId,
        String seniorName,
        EnumValueDto health,
        int openJourneys,
        int completedJourneys,
        int overdueJourneys,
        int averageProgress,
        CurrentJourneyDto current,
        LocalDateTime lastActivityAt,
        LocalDate nextDueDate,
        int attentionCount
) {}
