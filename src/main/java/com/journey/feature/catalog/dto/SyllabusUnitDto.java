package com.journey.feature.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A unit in a journey's catalog outline: its items (titles and first lines, no content) and how many quiz
 * questions it ends with. Answers are never included here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyllabusUnitDto(
        int order,
        String title,
        String description,
        List<SyllabusEntryDto> items,
        int quizQuestions
) {}
