package com.journey.feature.journey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateJourneyRequest(
        @NotBlank String title,
        String description,
        String techTag,
        String createdById,
        String createdByName,
        @NotEmpty List<ItemPayload> items
) {
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
