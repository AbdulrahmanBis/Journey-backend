package com.journey.feature.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Create or edit an announcement.
 *
 * @param orgWide       everyone in the company (HR and Admin only)
 * @param departmentIds the departments it reaches when not org-wide; a Manager may leave it empty
 *                      for their own department
 * @param showUntil     last day on dashboards; defaults to two weeks from today
 * @param notifyAgain   on edit, notify the audience again (a new announcement always notifies)
 */
public record SaveAnnouncementRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 50_000) String body,
        Boolean orgWide,
        List<String> departmentIds,
        LocalDate showUntil,
        Boolean notifyAgain
) {}
