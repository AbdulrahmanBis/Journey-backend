package com.journey.feature.gettingStarted.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

import java.util.List;

/**
 * The caller's getting-started checklist. Empty steps mean their role has none.
 *
 * @param dismissed the person hid the checklist
 */
public record GettingStartedDto(EnumValueDto role, boolean dismissed, List<Step> steps) {

    /** @param key translation key suffix; @param link where to do it (null when it is not up to them) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Step(String key, boolean done, String link) {}
}
