package com.journey.feature.journey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateJourneyRequest(
        @NotBlank String title,
        String description,
        String techTag,
        /** Optional expected duration in days. */
        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(730) Integer targetDays,
        String createdById,
        String createdByName,
        /** Flat list, for a journey with a single unit. Ignored when {@code units} is sent. */
        List<ItemPayload> items,
        /** The journey's units in order, each with its items and optional quiz. */
        List<UnitPayload> units
) {
    /**
     * @param id   present when editing an existing unit; its items' progress is kept
     * @param quiz up to five auto-graded questions (multiple choice or yes/no)
     */
    public record UnitPayload(
            String id,
            @NotBlank String title,
            String description,
            List<ItemPayload> items,
            List<com.journey.feature.exam.dto.QuestionDraftDto> quiz
    ) {}

    public record ItemPayload(
            String id,          // present on update (reuse existing), null on create
            @NotBlank String title,
            String description, // rich HTML from the editor
            List<AttachmentPayload> attachments
    ) {}

    /**
     * Uploaded kinds send the {@code storageKey} returned by POST /api/files; external kinds send a
     * {@code url}. The service validates that the right one is present for the kind.
     */
    public record AttachmentPayload(
            String id,
            @NotNull Integer kind,   // AttachmentKind code
            String label,
            String url,
            String storageKey,
            String mimeType,
            Long sizeBytes,
            String originalName
    ) {}
}
