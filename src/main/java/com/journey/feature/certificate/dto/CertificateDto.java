package com.journey.feature.certificate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;
import com.journey.feature.department.dto.DepartmentDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A certificate as its page shows it. The learner's name and department are current (a renamed person's
 * certificate shows the new name); the title and dates are as earned.
 *
 * @param reviewerName who reviewed the journey or assigned the package
 * @param hours        learning time logged on it
 * @param journeys     a package's completed journeys, in order
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CertificateDto(
        String id,
        String code,
        EnumValueDto type,
        String title,
        String learnerId,
        String learnerName,
        DepartmentDto department,
        String learnerJourneyId,
        String packageAssignmentId,
        Integer examScorePercent,
        double hours,
        String reviewerName,
        List<String> journeys,
        LocalDateTime completedAt,
        LocalDateTime issuedAt
) {}
