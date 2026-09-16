package com.journey.feature.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A line of a catalog entry's outline: a quest item of a journey, or a journey of a package.
 *
 * @param itemCount for a package's journey, how many quest items it has
 * @param journeyId for a package's journey, so the page can link to its own catalog entry
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyllabusEntryDto(
        int position,
        String title,
        String description,
        String tag,
        Integer itemCount,
        String journeyId
) {}
