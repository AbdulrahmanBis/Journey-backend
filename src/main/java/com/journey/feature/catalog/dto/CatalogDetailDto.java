package com.journey.feature.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogDetailDto(
        CatalogEntryDto entry,
        /** A package's journeys in order (absent for a journey). */
        List<SyllabusEntryDto> syllabus,
        /** A journey's units with their items (absent for a package). */
        List<SyllabusUnitDto> units,
        String reviewerName
) {}
