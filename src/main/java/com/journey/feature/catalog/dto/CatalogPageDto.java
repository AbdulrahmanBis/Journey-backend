package com.journey.feature.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * @param statuses     empty for staff: status is the learner's own standing
 * @param reviewerName who reviews a learner's self-enrollments; absent for staff, or when the
 *                     learner has neither a senior nor a department manager (enrolling is refused)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogPageDto(
        List<CatalogEntryDto> entries,
        long total,
        List<FacetDto> types,
        List<FacetDto> tags,
        List<FacetDto> statuses,
        String reviewerName
) {}
