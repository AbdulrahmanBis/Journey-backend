package com.journey.feature.department.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A department on the wire.
 *
 * <p>{@code english} / {@code arabic} rather than nameEn / nameAr on purpose: it is the same shape as
 * every enum value the API returns, so the front end's existing "pick the label for the current
 * language" helper works on departments unchanged.
 *
 * @param memberCount how many people are in it; null where the count is not needed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DepartmentDto(
        String id,
        String english,
        String arabic,
        Long memberCount
) {}
