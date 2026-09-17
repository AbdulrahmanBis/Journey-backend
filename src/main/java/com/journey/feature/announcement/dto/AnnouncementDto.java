package com.journey.feature.announcement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.feature.department.dto.DepartmentDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * An announcement as the dashboard and the announcements page see it.
 *
 * @param excerpt  the body as plain text, shortened — for cards and notifications
 * @param active   still within its show-until date
 * @param canEdit  the caller may edit or delete it
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnnouncementDto(
        String id,
        String title,
        String body,
        String excerpt,
        boolean orgWide,
        List<DepartmentDto> departments,
        LocalDate showUntil,
        boolean active,
        String authorId,
        String authorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canEdit
) {}
