package com.journey.feature.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One journey or package on the catalog shelf.
 *
 * @param itemCount     quest items for a journey; journeys for a package
 * @param journeyTitles a package's journeys in order (absent for a journey)
 * @param learnerCount  learners who currently hold it (not cancelled)
 * @param mine          absent for staff, who assign rather than enroll
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogEntryDto(
        EnumValueDto type,
        String id,
        String title,
        String description,
        List<String> tags,
        int itemCount,
        boolean hasExam,
        List<String> journeyTitles,
        long learnerCount,
        String createdByName,
        LocalDateTime updatedAt,
        MyProgressDto mine
) {}
