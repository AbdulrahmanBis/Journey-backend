package com.journey.feature.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogDetailDto(
        CatalogEntryDto entry,
        List<SyllabusEntryDto> syllabus,
        String reviewerName
) {}
