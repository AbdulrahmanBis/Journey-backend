package com.journey.feature.learnerJourney.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.feature.journey.dto.AttachmentDto;

import java.util.List;

/**
 * A template item joined with this learner's progress.
 *
 * <p>{@code description} is rich HTML from the editor, and {@code attachments} carries the item's
 * links, files and embeds — without it the learner would never see any of the content a senior
 * attached.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JourneyItemWithProgressDto(
        // from JourneyItem (template)
        String id,
        String journeyId,
        String title,
        String description,
        Integer order,
        List<AttachmentDto> attachments,
        // learner-specific progress
        LearnerJourneyItemDto progress
) {}
