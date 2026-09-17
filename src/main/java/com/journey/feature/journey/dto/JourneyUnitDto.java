package com.journey.feature.journey.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.feature.exam.dto.ExamQuestionDto;

import java.util.List;

/** A unit as authors edit it: items with content, and quiz questions with their answers. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JourneyUnitDto(
        String id,
        String title,
        String description,
        int order,
        List<JourneyItemDto> items,
        List<ExamQuestionDto> quiz
) {}
