package com.journey.feature.journey.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code description} is rich HTML produced by the editor (it used to be plain text with newlines),
 * so clients must render it as HTML rather than splitting on line breaks.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JourneyItemDto(
        String id,
        String journeyId,
        String title,
        String description,
        Integer order,
        List<AttachmentDto> attachments
) {}
