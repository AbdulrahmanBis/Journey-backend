package com.journey.feature.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

/**
 * One filter option with how many entries it would show, given every other active filter.
 *
 * @param value the query-parameter value that selects it (an enum code, or the tag itself)
 * @param label present for enum facets; tags are their own label
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FacetDto(
        String value,
        EnumValueDto label,
        long count
) {}
